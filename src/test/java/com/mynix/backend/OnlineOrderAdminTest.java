package com.mynix.backend;

import com.mynix.backend.model.Category;
import com.mynix.backend.model.Product;
import com.mynix.backend.model.User;
import com.mynix.backend.model.UserRole;
import com.mynix.backend.repository.CategoryRepository;
import com.mynix.backend.repository.ProductRepository;
import com.mynix.backend.repository.UserRepository;
import com.mynix.backend.service.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
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

/** The POS "Online Orders" screen's API: staff move website orders along. */
@IntegrationTest
class OnlineOrderAdminTest {

    private static final String PASSWORD = "test-password-123";

    @Autowired Environment environment;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean SmsService smsService;
    final Map<String, String> sentCodes = new ConcurrentHashMap<>();

    RestClient http;
    String storeToken;
    String cashier;
    String cashierToken;
    Product torch;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Matcher m = Pattern.compile("code: ([0-9]{6})").matcher(invocation.getArgument(1, String.class));
            if (m.find()) sentCodes.put(invocation.getArgument(0, String.class), m.group(1));
            return null;
        }).when(smsService).sendSms(anyString(), anyString());

        http = RestClient.builder()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        storeToken = login(user("store-" + suffix, UserRole.ONLINE_STORE));
        cashier = user("cashier-" + suffix, UserRole.CASHIER);
        cashierToken = login(cashier);

        Category category = categoryRepository.save(Category.builder().name("Loupes " + suffix).build());
        torch = productRepository.save(Product.builder()
                .name("Loupe " + suffix).barcode("L-" + suffix).category(category)
                .buyingPrice(new BigDecimal("1000.00")).sellingPrice(new BigDecimal("3000.00"))
                .stockQuantity(5).build());
    }

    @Test
    void onlyStaffSeeOnlineOrders() {
        assertThat(get("/api/online-orders", storeToken).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/api/online-orders", null).getStatusCode().value()).isIn(401, 403);
        assertThat(get("/api/online-orders", cashierToken).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void staffSeeEverythingNeededToPackAndDeliver() {
        String phone = newPhone();
        String invoice = placeCodOrder(phone, 2);

        @SuppressWarnings("unchecked")
        Map<String, Object> order = (Map<String, Object>) get("/api/online-orders/" + invoice, cashierToken).getBody();
        assertThat(order).containsEntry("status", "PLACED").containsEntry("customerPhone", phone)
                .containsEntry("addressLine1", "12 Temple Road").containsEntry("district", "Ratnapura")
                .containsEntry("deliveryNotes", "Call before coming");
        assertThat((List<?>) order.get("items")).hasSize(1);
        assertThat(((List<?>) get("/api/online-orders?status=PLACED", cashierToken).getBody()))
                .anyMatch(o -> invoice.equals(((Map<?, ?>) o).get("invoiceNumber")));
    }

    @Test
    void packDispatchDeliverRecordsTheCashCollected() {
        String invoice = placeCodOrder(newPhone(), 2);
        long customerId = jdbc.queryForObject("SELECT customer_id FROM sales WHERE invoice_number = ?", Long.class, invoice);
        assertThat(outstanding(customerId)).isEqualByComparingTo("6000.00");

        assertThat(action(invoice, "pack").getBody()).containsEntry("status", "PACKED")
                .containsEntry("statusUpdatedBy", cashier);
        assertThat(action(invoice, "dispatch").getBody()).containsEntry("status", "DISPATCHED");
        assertThat(action(invoice, "deliver").getBody()).containsEntry("status", "DELIVERED");

        assertThat(outstanding(customerId)).isEqualByComparingTo("0.00");
        assertThat(stock()).isEqualTo(3);
        // The customer's tracking page follows along.
        assertThat(storeGet("/api/store/orders/" + invoice + "?phone=" + phoneOf(invoice)).getBody())
                .containsEntry("status", "DELIVERED");
    }

    @Test
    void cancellingReturnsStockAndRemovesTheSale() {
        String invoice = placeCodOrder(newPhone(), 2);
        long customerId = jdbc.queryForObject("SELECT customer_id FROM sales WHERE invoice_number = ?", Long.class, invoice);
        assertThat(stock()).isEqualTo(3);

        assertThat(action(invoice, "dispatch").getStatusCode().value()).isEqualTo(200);
        ResponseEntity<Map> cancelled = action(invoice, "cancel");

        assertThat(cancelled.getStatusCode().value()).isEqualTo(200);
        assertThat(cancelled.getBody()).containsEntry("status", "CANCELLED");
        assertThat(stock()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales WHERE invoice_number = ?", Integer.class, invoice)).isZero();
        assertThat(outstanding(customerId)).isEqualByComparingTo("0.00");
    }

    @Test
    void ordersOnlyMoveForward() {
        String delivered = placeCodOrder(newPhone(), 1);
        action(delivered, "deliver");
        assertThat(action(delivered, "cancel").getStatusCode().value()).isEqualTo(400);
        assertThat(action(delivered, "pack").getStatusCode().value()).isEqualTo(400);

        String cancelled = placeCodOrder(newPhone(), 1);
        action(cancelled, "cancel");
        assertThat(action(cancelled, "deliver").getStatusCode().value()).isEqualTo(400);

        String packed = placeCodOrder(newPhone(), 1);
        action(packed, "pack");
        assertThat(action(packed, "pack").getStatusCode().value()).isEqualTo(400);
        assertThat(action(packed, "explode").getStatusCode().value()).isEqualTo(400);
    }

    // --- helpers ---------------------------------------------------------------

    private String placeCodOrder(String phone, int quantity) {
        jdbc.update("UPDATE customer_otps SET created_at = created_at - interval '2 minutes' WHERE phone = ?", phone);
        storePost("/api/store/verification/send", Map.of("phone", phone, "purpose", "CHECKOUT"));
        String token = (String) storePost("/api/store/verification/check",
                Map.of("phone", phone, "purpose", "CHECKOUT", "code", sentCodes.get(phone))).getBody().get("verificationToken");
        ResponseEntity<Map> placed = storePost("/api/store/orders", Map.ofEntries(
                Map.entry("requestId", UUID.randomUUID().toString()),
                Map.entry("verificationToken", token),
                Map.entry("items", List.of(Map.of("barcode", torch.getBarcode(), "quantity", quantity))),
                Map.entry("paymentMethod", "CASH_ON_DELIVERY"),
                Map.entry("deliveryFee", 0),
                Map.entry("customerName", "Nimal Perera"),
                Map.entry("customerPhone", phone),
                Map.entry("addressLine1", "12 Temple Road"),
                Map.entry("city", "Ratnapura"),
                Map.entry("district", "Ratnapura"),
                Map.entry("deliveryNotes", "Call before coming")));
        assertThat(placed.getStatusCode().value()).isEqualTo(201);
        return (String) placed.getBody().get("invoiceNumber");
    }

    private String phoneOf(String invoice) {
        return jdbc.queryForObject("SELECT customer_phone FROM online_orders WHERE invoice_number = ?", String.class, invoice);
    }

    private ResponseEntity<Map> action(String invoice, String action) {
        return http.post().uri("/api/online-orders/{invoice}/{action}", invoice, action)
                .header("Authorization", "Bearer " + cashierToken).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Object> get(String path, String token) {
        RestClient.RequestHeadersSpec<?> spec = http.get().uri(path);
        if (token != null) spec = spec.header("Authorization", "Bearer " + token);
        return spec.retrieve().toEntity(Object.class);
    }

    private ResponseEntity<Map> storeGet(String path) {
        return http.get().uri(path).header("Authorization", "Bearer " + storeToken).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> storePost(String path, Object body) {
        return http.post().uri(path).header("Authorization", "Bearer " + storeToken)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(Map.class);
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

    private static String newPhone() {
        return "07" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
    }

    private BigDecimal outstanding(long customerId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN type = 'PAYMENT' THEN -amount ELSE amount END), 0)
                FROM customer_transactions WHERE customer_id = ?""", BigDecimal.class, customerId);
    }

    private int stock() {
        return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, torch.getId());
    }
}
