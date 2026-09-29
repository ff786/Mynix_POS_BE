package com.mynix.backend.security;

import com.mynix.backend.model.User;
import com.mynix.backend.model.UserRole;
import com.mynix.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Emergency admin password reset, triggered only from the server's settings:
 * MYNIX_RESET_ADMIN_PASSWORD=<any new value, e.g. today's date> sets the admin
 * account's password to Admin@123 at startup, unlocks it, and makes the admin
 * choose a new password (confirmed by an SMS code) before anything else.
 * Each value works once, so leaving the setting in place doesn't reset again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminPasswordReset implements ApplicationRunner {

    public static final String TEMPORARY_PASSWORD = "Admin@123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    @Value("${mynix.admin-reset.token:}")
    private String resetToken;

    @Value("${mynix.admin-reset.username:admin}")
    private String username;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (resetToken == null || resetToken.isBlank()) return;

        String tokenHash = sha256(resetToken.trim());
        Integer used = jdbc.queryForObject(
                "SELECT count(*) FROM admin_password_resets WHERE token_hash = ?", Integer.class, tokenHash);
        if (used != null && used > 0) {
            log.warn("MYNIX_RESET_ADMIN_PASSWORD was already used; remove it from the server settings.");
            return;
        }

        User admin = userRepository.findByUsername(username.trim().toLowerCase())
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .orElse(null);
        if (admin == null) {
            log.error("Admin password reset: no ADMIN account named '{}' (set MYNIX_RESET_ADMIN_USERNAME).", username);
            return;
        }

        admin.setPasswordHash(passwordEncoder.encode(TEMPORARY_PASSWORD));
        admin.setActive(true);
        admin.setMustChangePassword(true);
        admin.setUpdatedAt(LocalDateTime.now());
        userRepository.save(admin);
        jdbc.update("INSERT INTO admin_password_resets (token_hash, username) VALUES (?, ?)", tokenHash, admin.getUsername());

        log.warn("Admin password for '{}' was reset to the temporary password. Sign in and choose a new one now, "
                + "then remove MYNIX_RESET_ADMIN_PASSWORD from the server settings.", admin.getUsername());
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
