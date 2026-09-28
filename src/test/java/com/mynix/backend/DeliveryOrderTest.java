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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Phone / WhatsApp orders taken in New Sale with "Deliver this order". */
@IntegrationTest
class DeliveryOrderTest {

    private static final String PASSWORD = "test-password-123";

    @Autowired Environment environment;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean SmsService smsService;
    final List<String[]> sent = new CopyOnWriteArrayList<>();

    RestClient http;
    String cashierToken;
    Product loupe;
    Customer customer;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            sent.add(new String[]{invocation.getArgument(0), invocation.getArgument(1)});
            return null;
        }).when(smsService).sendSms(anyString(), anyString());
        http = RestClient.builder()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String cashier = "cashier-" + suffix;
        userRepository.save(User.builder().fullName(cashier).username(cashier)
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(UserRole.CASHIER).active(true).build());
        cashierToken = (String) http.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", cashier, "password", PASSWORD)).retrieve().body(Map.class).get("token");

        Category category = categoryRepository.save(Category.builder().name("Del " + suffix).build());
        loupe = productRepository.save(Product.builder().name("Loupe " + suffix).barcode("D-" + suffix)
                .category(category).buyingPrice(new BigDecimal("1000.00")).sellingPrice(new BigDecimal("2000.00"))
                .stockQuantity(10).build());
        customer = customerRepository.save(Customer.builder().name("Phone Buyer")
                .contactNumber("07" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999))
                .createdAt(LocalDateTime.now()).build());
    }

    @Test
    void codPhoneOrderAppearsInOnlineOrdersWithTheDeliverySms() {
        Map<String, Object> delivery = typedAddress("PHONE");
        delivery.put("notes", "Call on arrival");
        ResponseEntity<Map> sale = checkout("CREDIT", delivery);
        assertThat(sale.getStatusCode().value()).isEqualTo(200);
        String invoice = (String) sale.getBody().get("invoiceNumber");

        Map<?, ?> order = staff(HttpMethod.GET, "/api/online-orders/" + invoice, null).getBody();
        assertThat(order.get("channel")).isEqualTo("PHONE");
        assertThat(order.get("paymentMethod")).isEqualTo("CASH_ON_DELIVERY");
        assertThat(order.get("addressLine1")).isEqualTo("5 Lake Drive");
        assertThat(order.get("deliveryNotes")).isEqualTo("Call on arrival");
        assertThat(lastSms()).contains("Thank you for your order " + invoice).contains("to pay on delivery");
        verify(smsService, never()).sendInvoiceSms(any(), any(), any());

        // Delivered & paid clears the credit, exactly like website orders.
        assertThat(staff(HttpMethod.POST, "/api/online-orders/" + invoice + "/deliver", null).getStatusCode().value())
                .isEqualTo(200);
        assertThat(outstanding()).isEqualByComparingTo("0.00");
    }

    @Test
    void bankTransferOrderIsAlreadyPaid() {
        String invoice = (String) checkout("BANK_DEPOSIT", typedAddress("WHATSAPP")).getBody().get("invoiceNumber");

        Map<?, ?> order = staff(HttpMethod.GET, "/api/online-orders/" + invoice, null).getBody();
        assertThat(order.get("channel")).isEqualTo("WHATSAPP");
        assertThat(order.get("paymentMethod")).isEqualTo("BANK_TRANSFER");
        assertThat(lastSms()).contains("Paid by bank transfer");

        staff(HttpMethod.POST, "/api/online-orders/" + invoice + "/dispatch", null);
        assertThat(lastSms()).contains("is on its way").doesNotContain("ready for the courier");
        staff(HttpMethod.POST, "/api/online-orders/" + invoice + "/deliver", null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_transactions WHERE customer_id = ? AND type = 'PAYMENT'",
                Integer.class, customer.getId())).isZero();
    }

    @Test
    void deliveryOrdersNeedTheRightDetails() {
        assertThat(checkout("CASH", typedAddress("PHONE")).getStatusCode().value()).isEqualTo(400);
        assertThat(checkout("CREDIT", typedAddress("WEBSITE")).getStatusCode().value()).isEqualTo(400);
        Map<String, Object> noAddress = new HashMap<>(Map.of("channel", "PHONE"));
        assertThat(checkout("CREDIT", noAddress).getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, loupe.getId()))
                .isEqualTo(10);
    }

    @Test
    void staffKeepAddressesAndReuseThem() {
        Map<String, Object> delivery = typedAddress("PHONE");
        delivery.put("saveAddress", true);
        delivery.put("label", "Home");
        checkout("CREDIT", delivery);

        List<?> saved = http.get().uri("/api/customers/{id}/addresses", customer.getId())
                .header("Authorization", "Bearer " + cashierToken).retrieve().body(List.class);
        assertThat(saved).hasSize(1);
        long addressId = ((Number) ((Map<?, ?>) saved.getFirst()).get("id")).longValue();

        String invoice = (String) checkout("CREDIT", new HashMap<>(Map.of("channel", "PHONE", "savedAddressId", addressId)))
                .getBody().get("invoiceNumber");
        assertThat(staff(HttpMethod.GET, "/api/online-orders/" + invoice, null).getBody().get("city")).isEqualTo("Kandy");

        // Another customer's address can't be used.
        Customer other = customerRepository.save(Customer.builder().name("Other").contactNumber("0770000001")
                .createdAt(LocalDateTime.now()).build());
        Map<String, Object> body = checkoutBody("CREDIT", new HashMap<>(Map.of("channel", "PHONE", "savedAddressId", addressId)));
        body.put("customerId", other.getId());
        assertThat(http.post().uri("/api/pos/checkout").header("Authorization", "Bearer " + cashierToken)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity()
                .getStatusCode().value()).isIn(400, 404);
    }

    @Test
    void shopSalesWithoutDeliveryAreUnchanged() {
        ResponseEntity<Map> sale = checkout("CASH", null);
        assertThat(sale.getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM online_orders WHERE invoice_number = ?", Integer.class,
                sale.getBody().get("invoiceNumber"))).isZero();
        verify(smsService).sendInvoiceSms(any(), any(), any());
    }

    // --- helpers ---------------------------------------------------------------

    private Map<String, Object> typedAddress(String channel) {
        Map<String, Object> d = new HashMap<>();
        d.put("channel", channel);
        d.put("addressLine1", "5 Lake Drive");
        d.put("city", "Kandy");
        d.put("district", "Kandy");
        return d;
    }

    private Map<String, Object> checkoutBody(String paymentMethod, Map<String, Object> delivery) {
        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(Map.of("barcode", loupe.getBarcode(), "quantity", 1)));
        body.put("paymentMethod", paymentMethod);
        body.put("discount", 0);
        body.put("deliveryFee", 350);
        body.put("customerId", customer.getId());
        if (delivery != null) body.put("delivery", delivery);
        return body;
    }

    private ResponseEntity<Map> checkout(String paymentMethod, Map<String, Object> delivery) {
        return http.post().uri("/api/pos/checkout").header("Authorization", "Bearer " + cashierToken)
                .contentType(MediaType.APPLICATION_JSON).body(checkoutBody(paymentMethod, delivery))
                .retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> staff(HttpMethod method, String path, Object body) {
        RestClient.RequestBodySpec spec = http.method(method).uri(path).header("Authorization", "Bearer " + cashierToken);
        if (body != null) spec.contentType(MediaType.APPLICATION_JSON).body(body);
        return spec.retrieve().toEntity(Map.class);
    }

    private String lastSms() {
        return sent.stream().filter(s -> s[0].equals(customer.getContactNumber())).reduce((a, b) -> b).map(s -> s[1]).orElse("");
    }

    private BigDecimal outstanding() {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN type = 'PAYMENT' THEN -amount ELSE amount END), 0)
                FROM customer_transactions WHERE customer_id = ?""", BigDecimal.class, customer.getId());
    }
}
