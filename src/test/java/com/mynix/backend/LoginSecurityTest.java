package com.mynix.backend;

import com.mynix.backend.model.User;
import com.mynix.backend.model.UserRole;
import com.mynix.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Staff sign-in: deactivated accounts, password guessing, bad tokens. */
@IntegrationTest
class LoginSecurityTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired Environment environment;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    void deactivatedStaffCannotSignIn() {
        User user = cashier();
        user.setActive(false);
        userRepository.save(user);

        assertThat(login(user.getUsername(), PASSWORD, "10.0.0.1").status()).isEqualTo(400);
    }

    @Test
    void deactivatingStaffEndsTheirExistingSession() {
        User user = cashier();
        String token = login(user.getUsername(), PASSWORD, "10.0.0.2").token();
        assertThat(get("/api/products", token)).isEqualTo(200);

        user.setActive(false);
        userRepository.save(user);

        assertThat(get("/api/products", token)).isIn(401, 403);
    }

    @Test
    void repeatedWrongPasswordsLockThatUsernameForThatAddress() {
        User user = cashier();
        for (int i = 0; i < 5; i++) {
            assertThat(login(user.getUsername(), "wrong", "10.0.0.3").status()).isEqualTo(400);
        }
        // Locked now — even the right password is refused from this address…
        assertThat(login(user.getUsername(), PASSWORD, "10.0.0.3").status()).isEqualTo(429);
        // …but the real user elsewhere is not locked out.
        assertThat(login(user.getUsername(), PASSWORD, "10.0.0.4").status()).isEqualTo(200);
    }

    @Test
    void oneAddressGuessingManyUsernamesIsBlocked() {
        for (int i = 0; i < 20; i++) {
            login("nobody-" + i, "wrong", "10.0.0.5");
        }
        User user = cashier();
        assertThat(login(user.getUsername(), PASSWORD, "10.0.0.5").status()).isEqualTo(429);
    }

    @Test
    void badTokensAreRejectedNotErrors() {
        assertThat(get("/api/products", "not.a.jwt")).isIn(401, 403);
        assertThat(get("/api/products", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.bad")).isIn(401, 403);
    }

    // --- helpers ---------------------------------------------------------------

    record Login(int status, String token) {}

    private User cashier() {
        String username = "cashier-" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder().fullName(username).username(username)
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(UserRole.CASHIER).active(true).build());
    }

    private Login login(String username, String password, String address) {
        var response = http.post().uri("/api/auth/login")
                .header("CF-Connecting-IP", address)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .retrieve().toEntity(Map.class);
        Object token = response.getBody() == null ? null : response.getBody().get("token");
        return new Login(response.getStatusCode().value(), (String) token);
    }

    private int get(String path, String token) {
        return http.get().uri(path).header("Authorization", "Bearer " + token)
                .retrieve().toBodilessEntity().getStatusCode().value();
    }

    @Test
    void malformedRequestsGetNoInternalDetails() {
        org.springframework.http.ResponseEntity<java.util.Map> response = org.springframework.web.client.RestClient.builder()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .defaultStatusHandler(status -> true, (req, res) -> { })
                .build()
                .post().uri("/api/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body("{bad json")
                .retrieve().toEntity(java.util.Map.class);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(response.getBody()).containsEntry("message", "The request isn't valid.");
    }
}
