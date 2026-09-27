package com.mynix.backend.service.impl;

import com.mynix.backend.model.CustomerOtp;
import com.mynix.backend.model.PhoneVerification;
import com.mynix.backend.model.VerificationPurpose;
import com.mynix.backend.repository.CustomerOtpRepository;
import com.mynix.backend.repository.PhoneVerificationRepository;
import com.mynix.backend.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * SMS one-time codes for the online store. A correct code yields a short-lived,
 * single-use token proving the phone number was verified. Codes and tokens are
 * stored only as hashes; limits live in the database so they hold across
 * servers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    static final int CODE_MINUTES = 5;
    static final int MAX_ATTEMPTS = 5;
    static final int RESEND_SECONDS = 60;
    static final int MAX_SENDS_PER_HOUR = 5;
    static final int TOKEN_MINUTES = 15;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerOtpRepository otpRepository;
    private final PhoneVerificationRepository verificationRepository;
    private final SmsService smsService;

    @Value("${jwt.secret}")
    private String secret;

    /** Local development only: log codes when SMS sending is switched off. */
    @Value("${mynix.otp.log-codes:false}")
    private boolean logCodes;

    @Value("${mynix.sms-enabled:true}")
    private boolean smsEnabled;

    public enum SendResult { SENT, TOO_SOON, TOO_MANY }

    @Transactional
    public SendResult sendCode(String phone, VerificationPurpose purpose) {

        LocalDateTime now = LocalDateTime.now();
        var latest = otpRepository.findFirstByPhoneAndPurposeOrderByCreatedAtDesc(phone, purpose);
        if (latest.isPresent() && latest.get().getCreatedAt().isAfter(now.minusSeconds(RESEND_SECONDS))) {
            return SendResult.TOO_SOON;
        }
        if (otpRepository.countByPhoneAndCreatedAtAfter(phone, now.minusHours(1)) >= MAX_SENDS_PER_HOUR) {
            return SendResult.TOO_MANY;
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otpRepository.save(CustomerOtp.builder()
                .phone(phone)
                .purpose(purpose)
                .codeHash(codeHash(phone, purpose, code))
                .expiresAt(now.plusMinutes(CODE_MINUTES))
                .createdAt(now)
                .build());

        if (!smsEnabled && logCodes) {
            log.warn("[local only] MYNIX verification code for {}: {}", phone, code);
        }
        smsService.sendSms(phone, "MYNIX verification code: " + code
                + "\nValid for " + CODE_MINUTES + " minutes. Never share this code.");
        return SendResult.SENT;
    }

    /** Checks a code; returns a verification token, or null if wrong/expired/used up. */
    @Transactional(noRollbackFor = RuntimeException.class)
    public String verifyCode(String phone, VerificationPurpose purpose, String code) {

        LocalDateTime now = LocalDateTime.now();
        CustomerOtp otp = otpRepository
                .findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(phone, purpose)
                .orElse(null);
        if (otp == null || otp.getExpiresAt().isBefore(now) || otp.getAttempts() >= MAX_ATTEMPTS) {
            return null;
        }

        otp.setAttempts(otp.getAttempts() + 1);
        boolean matches = code != null && code.matches("^[0-9]{6}$") && MessageDigest.isEqual(
                otp.getCodeHash().getBytes(StandardCharsets.UTF_8),
                codeHash(phone, purpose, code).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            otpRepository.save(otp);
            return null;
        }

        otp.setConsumedAt(now);
        otpRepository.save(otp);

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        verificationRepository.save(PhoneVerification.builder()
                .tokenHash(sha256(token))
                .phone(phone)
                .purpose(purpose)
                .expiresAt(now.plusMinutes(TOKEN_MINUTES))
                .createdAt(now)
                .build());
        return token;
    }

    /**
     * Uses up a verification token (joins the caller's transaction, so it's
     * only spent if the order/sign-in succeeds). Returns the verified phone,
     * or null if the token is unknown, expired, used or for another purpose.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String consumeToken(String token, VerificationPurpose purpose) {

        if (token == null || token.isBlank() || token.length() > 100) {
            return null;
        }
        PhoneVerification verification = verificationRepository.findByTokenHash(sha256(token)).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (verification == null || verification.getPurpose() != purpose
                || verification.getUsedAt() != null || verification.getExpiresAt().isBefore(now)) {
            return null;
        }
        verification.setUsedAt(now);
        verificationRepository.save(verification);
        return verification.getPhone();
    }

    private String codeHash(String phone, VerificationPurpose purpose, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((phone + "|" + purpose + "|" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash verification code", e);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
