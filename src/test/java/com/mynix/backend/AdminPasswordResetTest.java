package com.mynix.backend;

import com.mynix.backend.model.User;
import com.mynix.backend.model.UserRole;
import com.mynix.backend.repository.UserRepository;
import com.mynix.backend.security.AdminPasswordReset;
import com.mynix.backend.service.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/** Emergency admin reset from the server settings, then a forced password change with an SMS code. */
@IntegrationTest
class AdminPasswordResetTest {

    private static final Pattern CODE = Pattern.compile("is ([0-9]{6})");

    @Autowired Environment environment;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AdminPasswordReset adminPasswordReset;

    @MockitoBean SmsService smsService;
    final AtomicReference<String> lastCode = new AtomicReference<>();

    RestClient http;
    String admin;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Matcher m = CODE.matcher(invocation.getArgument(1, String.class));
            if (m.find()) lastCode.set(m.group(1));
            return null;
        }).when(smsService).sendSms(anyString(), anyString());

        http = RestClient.builder()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();

        admin = "owner-" + UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(User.builder().fullName("Owner").username(admin)
                .passwordHash(passwordEncoder.encode("forgotten-password-1")).role(UserRole.ADMIN).active(false).build());
    }

    @Test
    void resetOnceThenAChangeWithAnSmsCodeIsRequired() {
        String token = "reset-" + UUID.randomUUID();
        runReset(token);

        // Admin@123 now works, the account is active again, and a new password is required.
        Map<?, ?> login = login("Admin@123").getBody();
        assertThat(login.get("mustChangePassword")).isEqualTo(true);
        String jwt = (String) login.get("token");

        ResponseEntity<Map> blocked = http.get().uri("/api/products").header("Authorization", "Bearer " + jwt)
                .retrieve().toEntity(Map.class);
        assertThat(blocked.getStatusCode().value()).isEqualTo(403);
        assertThat(blocked.getBody()).containsEntry("code", "PASSWORD_CHANGE_REQUIRED");

        // A code by SMS, then the change.
        assertThat(post("/api/auth/password/code", jwt, Map.of()).getBody().get("sentTo")).asString().endsWith("815");
        assertThat(post("/api/auth/password", jwt, Map.of("code", "000000", "newPassword", "NewShopPass2026"))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(post("/api/auth/password", jwt, Map.of("code", lastCode.get(), "newPassword", "short1"))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(post("/api/auth/password", jwt, Map.of("code", lastCode.get(), "newPassword", "Admin@123"))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(post("/api/auth/password", jwt, Map.of("code", lastCode.get(), "newPassword", "NewShopPass2026"))
                .getStatusCode().value()).isEqualTo(200);

        Map<?, ?> after = login("NewShopPass2026").getBody();
        assertThat(after.get("mustChangePassword")).isEqualTo(false);
        assertThat(http.get().uri("/api/products").header("Authorization", "Bearer " + after.get("token"))
                .retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(200);
        assertThat(login("Admin@123").getStatusCode().value()).isEqualTo(400);

        // The same setting left in place doesn't reset again.
        runReset(token);
        assertThat(login("NewShopPass2026").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void passwordChangeNeedsASignedInUser() {
        assertThat(http.post().uri("/api/auth/password/code").contentType(MediaType.APPLICATION_JSON).body(Map.of())
                .retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(401);
    }

    private void runReset(String token) {
        ReflectionTestUtils.setField(adminPasswordReset, "resetToken", token);
        ReflectionTestUtils.setField(adminPasswordReset, "username", admin);
        adminPasswordReset.run(null);
        ReflectionTestUtils.setField(adminPasswordReset, "resetToken", "");
    }

    private ResponseEntity<Map> login(String password) {
        return http.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", admin, "password", password)).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> post(String path, String jwt, Object body) {
        return http.post().uri(path).header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(Map.class);
    }
}
