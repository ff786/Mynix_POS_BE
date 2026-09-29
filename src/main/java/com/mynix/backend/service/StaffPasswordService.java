package com.mynix.backend.service;

import com.mynix.backend.exception.TooManyAttemptsException;
import com.mynix.backend.model.User;
import com.mynix.backend.repository.UserRepository;
import com.mynix.backend.security.AdminPasswordReset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Changing a staff password takes two factors: being signed in, and a 6-digit
 * code sent by SMS to the shop's phone (MYNIX_ADMIN_PHONE).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffPasswordService {

    private static final int CODE_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RESEND_SECONDS = 60;

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsService smsService;
    private final SecureRandom random = new SecureRandom();

    @Value("${mynix.admin-phone:0778843815}")
    private String adminPhone;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${mynix.sms-enabled:true}")
    private boolean smsEnabled;

    @Value("${mynix.otp.log-codes:false}")
    private boolean logCodes;

    /** Sends a new code; returns the phone number with most digits hidden. */
    @Transactional
    public String sendCode(User user) {
        List<Map<String, Object>> pending = jdbc.queryForList(
                "SELECT sent_at FROM staff_password_codes WHERE user_id = ?", user.getId());
        if (!pending.isEmpty()) {
            LocalDateTime sentAt = ((Timestamp) pending.get(0).get("sent_at")).toLocalDateTime();
            if (sentAt.isAfter(LocalDateTime.now().minusSeconds(RESEND_SECONDS))) {
                throw new TooManyAttemptsException("Please wait a minute before asking for another code.");
            }
        }

        String code = "%06d".formatted(random.nextInt(1_000_000));
        jdbc.update("DELETE FROM staff_password_codes WHERE user_id = ?", user.getId());
        jdbc.update("INSERT INTO staff_password_codes (user_id, code_hash, expires_at, sent_at) VALUES (?, ?, ?, ?)",
                user.getId(), hash(code), Timestamp.valueOf(LocalDateTime.now().plusMinutes(CODE_MINUTES)),
                Timestamp.valueOf(LocalDateTime.now()));

        smsService.sendSms(adminPhone, "MYNIX POS: your code to change the password for " + user.getUsername()
                + " is " + code + ". It expires in " + CODE_MINUTES + " minutes. Don't share it.");
        if (!smsEnabled && logCodes) {
            log.info("Staff password code for {}: {} (SMS disabled locally)", user.getUsername(), code);
        }
        return mask(adminPhone);
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public void changePassword(User user, String code, String newPassword) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT code_hash, expires_at, attempts FROM staff_password_codes WHERE user_id = ?", user.getId());
        if (rows.isEmpty()) {
            throw new RuntimeException("Ask for a code first.");
        }
        Map<String, Object> row = rows.get(0);
        boolean expired = ((Timestamp) row.get("expires_at")).toLocalDateTime().isBefore(LocalDateTime.now());
        int attempts = ((Number) row.get("attempts")).intValue();
        if (expired || attempts >= MAX_ATTEMPTS) {
            jdbc.update("DELETE FROM staff_password_codes WHERE user_id = ?", user.getId());
            throw new RuntimeException("That code has expired. Ask for a new one.");
        }
        boolean matches = MessageDigest.isEqual(
                hash(code == null ? "" : code.trim()).getBytes(StandardCharsets.UTF_8),
                ((String) row.get("code_hash")).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            jdbc.update("UPDATE staff_password_codes SET attempts = attempts + 1 WHERE user_id = ?", user.getId());
            throw new RuntimeException("That code isn't right.");
        }

        String problem = passwordProblem(user, newPassword);
        if (problem != null) {
            throw new RuntimeException(problem);
        }

        User fresh = userRepository.findById(user.getId()).orElseThrow();
        fresh.setPasswordHash(passwordEncoder.encode(newPassword));
        fresh.setMustChangePassword(false);
        fresh.setUpdatedAt(LocalDateTime.now());
        userRepository.save(fresh);
        jdbc.update("DELETE FROM staff_password_codes WHERE user_id = ?", user.getId());
        log.info("Password changed for {}", user.getUsername());
    }

    private String passwordProblem(User user, String password) {
        if (password == null || password.length() < 10) {
            return "Use at least 10 characters.";
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            return "Use both letters and numbers.";
        }
        if (password.equals(AdminPasswordReset.TEMPORARY_PASSWORD)
                || password.equalsIgnoreCase(user.getUsername())
                || passwordEncoder.matches(password, user.getPasswordHash())) {
            return "Choose a password you haven't used here before.";
        }
        return null;
    }

    private String hash(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String mask(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return digits.length() < 4 ? "your phone" : "•••••••" + digits.substring(digits.length() - 3);
    }
}
