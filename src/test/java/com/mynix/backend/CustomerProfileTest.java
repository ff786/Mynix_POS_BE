package com.mynix.backend;

import com.mynix.backend.model.User;
import com.mynix.backend.model.UserRole;
import com.mynix.backend.repository.CustomerRepository;
import com.mynix.backend.repository.UserRepository;
import com.mynix.backend.service.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/** Website profile manager: name/email, saved addresses, closing the account. */
@IntegrationTest
class CustomerProfileTest {

    private static final String PASSWORD = "test-password-123";

    @Autowired Environment environment;
    @Autowired UserRepository userRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean SmsService smsService;
    final Map<String, String> codes = new ConcurrentHashMap<>();

    RestClient http;
    String storeToken;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Matcher m = Pattern.compile("code: ([0-9]{6})").matcher(invocation.getArgument(1, String.class));
            if (m.find()) codes.put(invocation.getArgument(0, String.class), m.group(1));
            return null;
        }).when(smsService).sendSms(anyString(), anyString());
        http = RestClient.builder()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        String username = "store-" + UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(User.builder().fullName(username).username(username)
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(UserRole.ONLINE_STORE).active(true).build());
        storeToken = (String) http.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", PASSWORD)).retrieve().body(Map.class).get("token");
    }

    @Test
    void customersEditTheirNameAndEmail() {
        long id = newAccount();
        assertThat(call(HttpMethod.PATCH, "/api/store/customers/" + id, Map.of("name", "Nimali Fernando")).getBody())
                .containsEntry("name", "Nimali Fernando");
        assertThat(call(HttpMethod.PATCH, "/api/store/customers/" + id, Map.of("email", "N@Example.com")).getBody())
                .containsEntry("email", "n@example.com").containsEntry("name", "Nimali Fernando");
        assertThat(call(HttpMethod.PATCH, "/api/store/customers/" + id, Map.of()).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void savedAddressesKeepExactlyOneDefault() {
        long id = newAccount();
        long home = addressId(call(HttpMethod.POST, addresses(id), address("Home", false)));
        long office = addressId(call(HttpMethod.POST, addresses(id), address("Office", true)));

        List<?> list = list(id);
        List<String> labels = list.stream().map(a -> String.valueOf(((Map<?, ?>) a).get("label"))).toList();
        assertThat(labels).containsExactly("Office", "Home");
        assertThat(defaults(list)).isEqualTo(1);

        assertThat(call(HttpMethod.POST, addresses(id) + "/" + home + "/default", null).getBody()).containsEntry("defaultAddress", true);
        assertThat(defaults(list(id))).isEqualTo(1);

        // Deleting the default promotes the remaining address.
        assertThat(call(HttpMethod.DELETE, addresses(id) + "/" + home, null).getStatusCode().value()).isEqualTo(204);
        List<?> remaining = list(id);
        assertThat(remaining).hasSize(1);
        assertThat(((Map<?, ?>) remaining.getFirst()).get("id")).isEqualTo((int) office);
        assertThat(defaults(remaining)).isEqualTo(1);
    }

    @Test
    void customersCannotTouchSomeoneElsesAddresses() {
        long alice = newAccount();
        long bob = newAccount();
        long aliceHome = addressId(call(HttpMethod.POST, addresses(alice), address("Home", true)));

        assertThat(call(HttpMethod.PUT, addresses(bob) + "/" + aliceHome, address("Mine now", false))
                .getStatusCode().value()).isEqualTo(404);
        assertThat(call(HttpMethod.DELETE, addresses(bob) + "/" + aliceHome, null).getStatusCode().value()).isEqualTo(404);
        assertThat(list(alice)).hasSize(1);
    }

    @Test
    void atMostTenAddresses() {
        long id = newAccount();
        for (int i = 0; i < 10; i++) {
            assertThat(call(HttpMethod.POST, addresses(id), address("A" + i, false)).getStatusCode().value()).isEqualTo(201);
        }
        assertThat(call(HttpMethod.POST, addresses(id), address("Too many", false)).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void closingTheAccountKeepsTheShopRecord() {
        long id = newAccount();
        call(HttpMethod.POST, addresses(id), address("Home", true));

        assertThat(call(HttpMethod.DELETE, "/api/store/customers/" + id + "/account", null).getStatusCode().value()).isEqualTo(204);

        assertThat(call(HttpMethod.GET, "/api/store/customers/" + id, null).getStatusCode().value()).isEqualTo(404);
        assertThat(customerRepository.findById(id)).isPresent();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_addresses WHERE customer_id = ?", Integer.class, id)).isZero();
    }

    // --- helpers ---------------------------------------------------------------

    private long newAccount() {
        String phone = "07" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
        call(HttpMethod.POST, "/api/store/verification/send", Map.of("phone", phone, "purpose", "ACCOUNT"));
        String token = (String) call(HttpMethod.POST, "/api/store/verification/check",
                Map.of("phone", phone, "purpose", "ACCOUNT", "code", codes.get(phone))).getBody().get("verificationToken");
        Map<?, ?> customer = call(HttpMethod.POST, "/api/store/customers/sign-in",
                Map.of("verificationToken", token, "name", "Test Customer", "email", "test@example.com")).getBody();
        return ((Number) customer.get("id")).longValue();
    }

    private static String addresses(long customerId) {
        return "/api/store/customers/" + customerId + "/addresses";
    }

    private static Map<String, Object> address(String label, boolean makeDefault) {
        Map<String, Object> a = new HashMap<>();
        a.put("label", label);
        a.put("addressLine1", "12 Temple Road");
        a.put("city", "Kandy");
        a.put("district", "Kandy");
        a.put("makeDefault", makeDefault);
        return a;
    }

    private static long addressId(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private List<?> list(long customerId) {
        return http.get().uri(addresses(customerId)).header("Authorization", "Bearer " + storeToken)
                .retrieve().body(List.class);
    }

    private static long defaults(List<?> addresses) {
        return addresses.stream().filter(a -> Boolean.TRUE.equals(((Map<?, ?>) a).get("defaultAddress"))).count();
    }

    private ResponseEntity<Map> call(HttpMethod method, String path, Object body) {
        RestClient.RequestBodySpec spec = http.method(method).uri(path).header("Authorization", "Bearer " + storeToken);
        if (body != null) spec.contentType(MediaType.APPLICATION_JSON).body(body);
        return spec.retrieve().toEntity(Map.class);
    }
}
