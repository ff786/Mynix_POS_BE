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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** SMS for website orders, and that shop sales keep their usual SMS. */
@IntegrationTest
class OrderSmsTest {

    private static final String PASSWORD = "test-password-123";

    @Autowired Environment environment;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean SmsService smsService;
    record Sms(String phone, String text) {}
    final List<Sms> sent = new CopyOnWriteArrayList<>();

    RestClient http;
    String storeToken;
    String cashierToken;
    Product loupe;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            sent.add(new Sms(invocation.getArgument(0), invocation.getArgument(1)));
            return null;
        }).when(smsService).sendSms(anyString(), anyString());

        http = RestClient.builder()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        storeToken = login(user("store-" + suffix, UserRole.ONLINE_STORE));
        cashierToken = login(user("cashier-" + suffix, UserRole.CASHIER));
        Category category = categoryRepository.save(Category.builder().name("Sms " + suffix).build());
        loupe = productRepository.save(Product.builder()
                .name("Loupe " + suffix).barcode("S-" + suffix).category(category)
                .buyingPrice(new BigDecimal("1000.00")).sellingPrice(new BigDecimal("2500.00"))
                .stockQuantity(10).build());
    }

    @Test
    void orderPlacedSmsLinksToTheWebsiteAndMentionsOnlyThisOrder() {
        String phone = newPhone();
        // A shop customer who already owes money from a shop credit sale.
        Customer customer = customerRepository.save(Customer.builder().name("Credit Regular")
                .contactNumber(phone).createdAt(LocalDateTime.now()).build());
        jdbc.update("INSERT INTO customer_transactions (customer_id, type, amount, description) VALUES (?, 'CREDIT_SALE', 9999, 'old')",
                customer.getId());

        String invoice = placeOrder(phone, 2);

        String text = lastTo(phone);
        assertThat(text).contains("Thank you for your order " + invoice).contains("Rs. 5,000.00")
                .contains("to pay on delivery").doesNotContain("outstanding").doesNotContain("9,999")
                .doesNotContainIgnoringCase("pos");
        String token = jdbc.queryForObject("SELECT public_invoice_token FROM sales WHERE invoice_number = ?", String.class, invoice);
        assertThat(text).contains("/invoice/" + token);
        verify(smsService, never()).sendInvoiceSms(any(), any(), any());
    }

    @Test
    void dispatchDeliverAndCancelEachTextTheCustomer() {
        String phone = newPhone();
        String invoice = placeOrder(phone, 1);

        action(invoice, "dispatch");
        assertThat(lastTo(phone)).contains(invoice + " is on its way").contains("keep Rs. 2,500.00 ready").contains("/track");

        action(invoice, "deliver");
        assertThat(lastTo(phone)).contains(invoice + " has been delivered").contains("received your payment of Rs. 2,500.00");
        verify(smsService, never()).sendPaymentSms(any(), any(), any(), any());

        String phone2 = newPhone();
        String invoice2 = placeOrder(phone2, 1);
        action(invoice2, "cancel");
        assertThat(lastTo(phone2)).contains(invoice2 + " has been cancelled");
    }

    @Test
    void shopSalesStillSendTheShopInvoiceSms() {
        Customer customer = customerRepository.save(Customer.builder().name("Shop Buyer")
                .contactNumber(newPhone()).createdAt(LocalDateTime.now()).build());
        ResponseEntity<Map> sale = http.post().uri("/api/pos/checkout").header("Authorization", "Bearer " + cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("items", List.of(Map.of("barcode", loupe.getBarcode(), "quantity", 1)),
                        "paymentMethod", "CASH", "discount", 0, "deliveryFee", 0, "customerId", customer.getId()))
                .retrieve().toEntity(Map.class);
        assertThat(sale.getStatusCode().value()).isEqualTo(200);
        verify(smsService).sendInvoiceSms(any(), any(), any());
    }

    @Test
    void invoiceLinkShowsWebsiteOrdersOnly() {
        String invoice = placeOrder(newPhone(), 1);
        String token = jdbc.queryForObject("SELECT public_invoice_token FROM sales WHERE invoice_number = ?", String.class, invoice);

        ResponseEntity<Map> found = storeGet("/api/store/invoices/" + token);
        assertThat(found.getStatusCode().value()).isEqualTo(200);
        assertThat(found.getBody()).containsEntry("invoiceNumber", invoice).containsEntry("paymentMethod", "CASH_ON_DELIVERY")
                .doesNotContainKeys("customerOutstanding", "customerContactNumber");

        String shopToken = jdbc.queryForObject("""
                SELECT s.public_invoice_token FROM sales s
                WHERE NOT EXISTS (SELECT 1 FROM online_orders o WHERE o.sale_id = s.id)
                  AND s.public_invoice_token IS NOT NULL LIMIT 1""", String.class);
        assertThat(storeGet("/api/store/invoices/" + shopToken).getStatusCode().value()).isEqualTo(404);
        assertThat(storeGet("/api/store/invoices/not-a-token").getStatusCode().value()).isEqualTo(400);
    }

    // --- helpers ---------------------------------------------------------------

    private String placeOrder(String phone, int quantity) {
        jdbc.update("UPDATE customer_otps SET created_at = created_at - interval '2 minutes' WHERE phone = ?", phone);
        storePost("/api/store/verification/send", Map.of("phone", phone, "purpose", "CHECKOUT"));
        Matcher m = Pattern.compile("code: ([0-9]{6})").matcher(lastTo(phone));
        assertThat(m.find()).isTrue();
        String token = (String) storePost("/api/store/verification/check",
                Map.of("phone", phone, "purpose", "CHECKOUT", "code", m.group(1))).getBody().get("verificationToken");
        ResponseEntity<Map> placed = storePost("/api/store/orders", Map.ofEntries(
                Map.entry("requestId", UUID.randomUUID().toString()),
                Map.entry("verificationToken", token),
                Map.entry("items", List.of(Map.of("barcode", loupe.getBarcode(), "quantity", quantity))),
                Map.entry("paymentMethod", "CASH_ON_DELIVERY"),
                Map.entry("deliveryFee", 0),
                Map.entry("customerName", "Online Buyer"),
                Map.entry("customerPhone", phone),
                Map.entry("addressLine1", "1 Main Street"),
                Map.entry("city", "Galle"),
                Map.entry("district", "Galle")));
        assertThat(placed.getStatusCode().value()).isEqualTo(201);
        return (String) placed.getBody().get("invoiceNumber");
    }

    private String lastTo(String phone) {
        return sent.stream().filter(s -> s.phone().equals(phone)).reduce((a, b) -> b).map(Sms::text).orElse("");
    }

    private void action(String invoice, String action) {
        assertThat(http.post().uri("/api/online-orders/{i}/{a}", invoice, action)
                .header("Authorization", "Bearer " + cashierToken).retrieve().toBodilessEntity()
                .getStatusCode().value()).isEqualTo(200);
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
}
