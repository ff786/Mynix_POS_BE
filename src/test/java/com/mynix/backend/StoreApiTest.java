package com.mynix.backend;

import com.mynix.backend.model.Category;
import com.mynix.backend.model.Customer;
import com.mynix.backend.model.Product;
import com.mynix.backend.model.User;
import com.mynix.backend.model.UserRole;
import com.mynix.backend.repository.CategoryRepository;
import com.mynix.backend.repository.CustomerRepository;
import com.mynix.backend.repository.ProductRepository;
import com.mynix.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The online store API over real HTTP: permissions, orders, and that staff flows still work. */
@IntegrationTest
class StoreApiTest {

    private static final String PASSWORD = "test-password-123";

    @Autowired Environment environment;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    RestClient http;
    String storeToken;
    String cashierToken;
    String adminToken;
    Product torch;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        storeToken = login(user("store-" + suffix, UserRole.ONLINE_STORE));
        cashierToken = login(user("cashier-" + suffix, UserRole.CASHIER));
        adminToken = login(user("admin-" + suffix, UserRole.ADMIN));

        Category category = categoryRepository.save(Category.builder().name("Torches " + suffix).build());
        torch = productRepository.save(Product.builder()
                .name("Gem torch " + suffix).barcode("T-" + suffix).category(category)
                .buyingPrice(new BigDecimal("1000.00")).sellingPrice(new BigDecimal("2500.00"))
                .stockQuantity(5).build());
        productRepository.save(Product.builder()
                .name("Retired torch " + suffix).barcode("R-" + suffix).category(category)
                .buyingPrice(new BigDecimal("1000.00")).sellingPrice(new BigDecimal("2000.00"))
                .stockQuantity(5).active(false).build());
    }

    // --- catalogue --------------------------------------------------------------

    @Test
    void storeSeesActiveProductsWithoutBuyingPrices() {
        ResponseEntity<List> response = http.get().uri("/api/store/products")
                .header("Authorization", "Bearer " + storeToken).retrieve().toEntity(List.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = response.getBody();
        Map<String, Object> mine = products.stream()
                .filter(p -> torch.getBarcode().equals(p.get("barcode"))).findFirst().orElseThrow();
        assertThat(mine).containsEntry("price", 2500.00).containsEntry("availableQuantity", 5);
        assertThat(mine).doesNotContainKeys("buyingPrice", "minimumStock", "active", "createdAt");
        assertThat(products).noneMatch(p -> ((String) p.get("barcode")).startsWith("R-"));
    }

    // --- permissions ------------------------------------------------------------

    @Test
    void storeAccountCannotReachAnyStaffEndpoint() {
        for (String path : List.of("/api/products", "/api/sales", "/api/customers", "/api/users",
                "/api/categories", "/api/dashboard", "/api/test")) {
            assertThat(status(HttpMethod.GET, path, storeToken, null)).as(path).isEqualTo(403);
        }
        assertThat(status(HttpMethod.POST, "/api/pos/checkout", storeToken,
                Map.of("items", List.of(Map.of("barcode", torch.getBarcode(), "quantity", 1)),
                        "paymentMethod", "CASH"))).isEqualTo(403);
    }

    @Test
    void staffAndVisitorsCannotUseTheStoreApi() {
        assertThat(status(HttpMethod.GET, "/api/store/products", cashierToken, null)).isEqualTo(403);
        assertThat(status(HttpMethod.GET, "/api/store/products", adminToken, null)).isEqualTo(403);
        assertThat(status(HttpMethod.GET, "/api/store/products", null, null)).isIn(401, 403);
    }

    @Test
    void staffFlowsStillWork() {
        assertThat(status(HttpMethod.GET, "/api/products", cashierToken, null)).isEqualTo(200);
        assertThat(status(HttpMethod.GET, "/api/customers", cashierToken, null)).isEqualTo(200);
        assertThat(status(HttpMethod.POST, "/api/pos/checkout", cashierToken,
                Map.of("items", List.of(Map.of("barcode", torch.getBarcode(), "quantity", 1)),
                        "paymentMethod", "CASH", "discount", 0, "deliveryFee", 0))).isEqualTo(200);
        assertThat(stock()).isEqualTo(4);
    }

    // --- orders -----------------------------------------------------------------

    @Test
    void cashOnDeliveryOrderBecomesACashSaleWithDeliveryFee() {
        ResponseEntity<Map> response = placeOrder(order(2, "CASH_ON_DELIVERY", null, "+94 77 123 4567"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsEntry("status", "PLACED").containsEntry("paymentMethod", "CASH_ON_DELIVERY")
                .containsEntry("subtotal", 5000.00).containsEntry("deliveryFee", 450.00)
                .containsEntry("grandTotal", 5450.00);
        assertThat(stock()).isEqualTo(3);

        Map<String, Object> sale = jdbc.queryForMap("""
                SELECT s.payment_method, s.delivery_fee, s.created_by, c.contact_number, c.name
                FROM sales s JOIN customers c ON c.id = s.customer_id
                WHERE s.invoice_number = ?""", body.get("invoiceNumber"));
        assertThat(sale.get("payment_method")).isEqualTo("CASH");
        assertThat((BigDecimal) sale.get("delivery_fee")).isEqualByComparingTo("450.00");
        assertThat((String) sale.get("created_by")).startsWith("store-");
        assertThat(sale.get("contact_number")).isEqualTo("0771234567");
        assertThat(sale.get("name")).isEqualTo("Nimal Perera");
    }

    @Test
    void retriedOrderIsNotPlacedTwice() {
        Map<String, Object> request = order(1, "CASH_ON_DELIVERY", null, "0771112222");

        String first = (String) placeOrder(request).getBody().get("invoiceNumber");
        String second = (String) placeOrder(request).getBody().get("invoiceNumber");

        assertThat(second).isEqualTo(first);
        assertThat(stock()).isEqualTo(4);
    }

    @Test
    void cardOrdersNeedAUniqueVerifiedPaymentReference() {
        assertThat(placeOrder(order(1, "CARD", null, "0771113333")).getStatusCode().value()).isEqualTo(400);

        String reference = "OP-" + UUID.randomUUID();
        ResponseEntity<Map> paid = placeOrder(order(1, "CARD", reference, "0771113333"));
        assertThat(paid.getStatusCode().value()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT payment_method FROM sales WHERE invoice_number = ?",
                String.class, paid.getBody().get("invoiceNumber"))).isEqualTo("CARD");

        ResponseEntity<Map> reused = placeOrder(order(1, "CARD", reference, "0771113333"));
        assertThat(reused.getStatusCode().value()).isEqualTo(400);
        assertThat(stock()).isEqualTo(4);
    }

    @Test
    void outOfStockOrderLeavesNothingBehind() {
        int ordersBefore = count("online_orders");
        int customersBefore = count("customers");

        ResponseEntity<Map> response = placeOrder(order(6, "CASH_ON_DELIVERY", null, "0779998888"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat((String) response.getBody().get("message")).contains("Insufficient stock");
        assertThat(stock()).isEqualTo(5);
        assertThat(count("online_orders")).isEqualTo(ordersBefore);
        assertThat(count("customers")).isEqualTo(customersBefore);
    }

    @Test
    void invalidOrderIsRejectedWithoutDetails() {
        Map<String, Object> request = order(1, "CASH_ON_DELIVERY", null, "0771234000");
        request.remove("addressLine1");
        ResponseEntity<Map> response = placeOrder(request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("message", "Invalid order details.");

        Map<String, Object> badPhone = order(1, "CASH_ON_DELIVERY", null, "0112345678");
        assertThat(placeOrder(badPhone).getStatusCode().value()).isEqualTo(400);
        assertThat(stock()).isEqualTo(5);
    }

    @Test
    void existingPosCustomerIsReusedAndNotModified() {
        String phone = "07" + (10000000 + (int) (Math.random() * 89999999));
        Customer existing = customerRepository.save(Customer.builder()
                .name("Shop Regular").contactNumber("+94" + phone.substring(1)).createdAt(LocalDateTime.now()).build());
        int customersBefore = count("customers");

        ResponseEntity<Map> response = placeOrder(order(1, "CASH_ON_DELIVERY", null, phone));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(count("customers")).isEqualTo(customersBefore);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM sales WHERE invoice_number = ?", Long.class,
                response.getBody().get("invoiceNumber"))).isEqualTo(existing.getId());
        assertThat(customerRepository.findById(existing.getId()).orElseThrow().getName()).isEqualTo("Shop Regular");
    }

    @Test
    void trackingNeedsTheRightPhoneAndShowsCancelledWhenStaffDeleteTheSale() {
        String invoice = (String) placeOrder(order(1, "CASH_ON_DELIVERY", null, "0775556666"))
                .getBody().get("invoiceNumber");

        assertThat(track(invoice, "0775556666").getStatusCode().value()).isEqualTo(200);
        assertThat(track(invoice, "0770000000").getStatusCode().value()).isEqualTo(404);

        // Staff delete the sale in the POS exactly as before (e.g. refused COD).
        assertThat(status(HttpMethod.DELETE, "/api/sales/" + invoice, adminToken, null)).isIn(200, 204);
        assertThat(track(invoice, "0775556666").getBody()).containsEntry("status", "CANCELLED");
    }

    // --- helpers ------------------------------------------------------------------

    private String user(String username, UserRole role) {
        userRepository.save(User.builder().fullName(username).username(username)
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(role).active(true).build());
        return username;
    }

    private String login(String username) {
        Map<?, ?> response = http.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", PASSWORD)).retrieve().body(Map.class);
        return (String) response.get("token");
    }

    private Map<String, Object> order(int quantity, String paymentMethod, String reference, String phone) {
        Map<String, Object> request = new HashMap<>();
        request.put("requestId", UUID.randomUUID().toString());
        request.put("items", List.of(Map.of("barcode", torch.getBarcode(), "quantity", quantity)));
        request.put("paymentMethod", paymentMethod);
        if (reference != null) request.put("paymentReference", reference);
        request.put("deliveryFee", 450);
        request.put("customerName", "Nimal Perera");
        request.put("customerPhone", phone);
        request.put("addressLine1", "12 Temple Road");
        request.put("city", "Ratnapura");
        request.put("district", "Ratnapura");
        return request;
    }

    private ResponseEntity<Map> placeOrder(Map<String, Object> request) {
        return http.post().uri("/api/store/orders").header("Authorization", "Bearer " + storeToken)
                .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> track(String invoice, String phone) {
        return http.get().uri("/api/store/orders/{invoice}?phone={phone}", invoice, phone)
                .header("Authorization", "Bearer " + storeToken).retrieve().toEntity(Map.class);
    }

    private int status(HttpMethod method, String path, String token, Object body) {
        RestClient.RequestBodySpec spec = http.method(method).uri(path);
        if (token != null) spec.header("Authorization", "Bearer " + token);
        if (body != null) spec.contentType(MediaType.APPLICATION_JSON).body(body);
        return spec.retrieve().toBodilessEntity().getStatusCode().value();
    }

    private int stock() {
        return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, torch.getId());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
