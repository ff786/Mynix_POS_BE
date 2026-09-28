package com.mynix.backend;

import com.mynix.backend.model.User;
import com.mynix.backend.model.UserRole;
import com.mynix.backend.repository.UserRepository;
import com.mynix.backend.service.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Newsletter sign-ups (moved from Supabase) and the admin list. */
@IntegrationTest
class NewsletterTest {

    private static final String PASSWORD = "test-password-123";

    @Autowired Environment environment;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean SmsService smsService;

    RestClient http;
    String storeToken;
    String adminToken;
    String cashierToken;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        storeToken = login(user("store-" + suffix, UserRole.ONLINE_STORE));
        adminToken = login(user("admin-" + suffix, UserRole.ADMIN));
        cashierToken = login(user("cashier-" + suffix, UserRole.CASHIER));
    }

    @Test
    void subscribesOnceAndTextsTheShop() {
        String email = "Reader-" + UUID.randomUUID().toString().substring(0, 6) + "@Example.com";
        assertThat(subscribe(email, hash("a")).getBody()).containsEntry("status", "SUBSCRIBED");
        assertThat(subscribe(email.toLowerCase(), hash("b")).getBody()).containsEntry("status", "ALREADY_SUBSCRIBED");

        verify(smsService, times(1)).sendSms(eq("0778843815"), contains(email.toLowerCase()));

        List<?> list = http.get().uri("/api/newsletter-subscribers").header("Authorization", "Bearer " + adminToken)
                .retrieve().body(List.class);
        assertThat(list).anyMatch(s -> email.toLowerCase().equals(((Map<?, ?>) s).get("email")));
    }

    @Test
    void oneVisitorCannotFloodSignUps() {
        String visitor = hash(UUID.randomUUID().toString());
        for (int i = 0; i < 5; i++) {
            assertThat(subscribe("flood" + i + "-" + UUID.randomUUID() + "@example.com", visitor).getBody())
                    .containsEntry("status", "SUBSCRIBED");
        }
        assertThat(subscribe("flood-last-" + UUID.randomUUID() + "@example.com", visitor).getBody())
                .containsEntry("status", "THROTTLED");
    }

    @Test
    void invalidSignUpsAreRejected() {
        assertThat(subscribe("not-an-email", hash("c")).getStatusCode().value()).isEqualTo(400);
        assertThat(subscribe("ok@example.com", "not-a-hash").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void onlyAdminsSeeSubscribers() {
        assertThat(status("/api/newsletter-subscribers", cashierToken)).isEqualTo(403);
        assertThat(status("/api/newsletter-subscribers", storeToken)).isEqualTo(403);
        assertThat(status("/api/newsletter-subscribers", adminToken)).isEqualTo(200);
    }

    private ResponseEntity<Map> subscribe(String email, String visitorHash) {
        return http.post().uri("/api/store/newsletter").header("Authorization", "Bearer " + storeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "visitorHash", visitorHash)).retrieve().toEntity(Map.class);
    }

    private int status(String path, String token) {
        return http.get().uri(path).header("Authorization", "Bearer " + token)
                .retrieve().toBodilessEntity().getStatusCode().value();
    }

    private static String hash(String seed) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(seed.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String user(String username, UserRole role) {
        userRepository.save(User.builder().fullName(username).username(username)
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(role).active(true).build());
        return username;
    }

    private String login(String username) {
        return (String) http.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", PASSWORD)).retrieve().body(Map.class).get("token");
    }
}
