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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/** The online store API over real HTTP: permissions, verification, orders, accounts. */
@IntegrationTest
class StoreApiTest {

    private static final String PASSWORD = "test-password-123";
    private static final Pattern CODE = Pattern.compile("code: ([0-9]{6})");

    @Autowired Environment environment;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    /** Stands in for text.lk: remembers the last code "sent" to each number. */
    @MockitoBean SmsService smsService;
    final Map<String, String> sentCodes = new ConcurrentHashMap<>();

    RestClient http;
    String storeToken;
    String cashierToken;
    String adminToken;
    Product torch;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Matcher m = CODE.matcher(invocation.getArgument(1, String.class));
            if (m.find()) sentCodes.put(invocation.getArgument(0, String.class), m.group(1));
            return null;
        }).when(smsService).sendSms(anyString(), anyString());

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

    // --- catalogue & permissions --------------------------------------------------

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

    // --- website product details ---------------------------------------------------

    @Test
    void websiteGetsTheFullNameAndSeoDetailsAndHiddenProductsStayOff() {
        torch.setFullName("MYNIX Professional Gem Torch " + torch.getBarcode());
        torch.setDescription("Bright white LED for inclusions.");
        torch.setSeoTitle("Gem Torch for Gemologists");
        torch.setSeoDescription("A bright LED gem torch.");
        productRepository.save(torch);

        Map<String, Object> mine = storeProducts().stream()
                .filter(p -> torch.getBarcode().equals(p.get("barcode"))).findFirst().orElseThrow();
        assertThat(mine)
                .containsEntry("name", torch.getFullName())
                .containsEntry("slug", torch.getSlug())
                .containsEntry("description", "Bright white LED for inclusions.")
                .containsEntry("seoTitle", "Gem Torch for Gemologists")
                .containsEntry("seoDescription", "A bright LED gem torch.");

        torch.setShowOnWebsite(false);
        productRepository.save(torch);
        assertThat(storeProducts()).noneMatch(p -> torch.getBarcode().equals(p.get("barcode")));
    }

    @Test
    void hiddenProductsCannotBeOrderedOnline() {
        torch.setShowOnWebsite(false);
        productRepository.save(torch);
        String phone = newPhone();

        ResponseEntity<Map> response = placeOrder(order(1, "CASH_ON_DELIVERY", phone, verifiedToken(phone, "CHECKOUT")));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(productRepository.findById(torch.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @Test
    void invoicesPrintTheFullName() {
        torch.setFullName("MYNIX Professional Gem Torch " + torch.getBarcode());
        productRepository.save(torch);
        String phone = newPhone();

        String invoice = (String) placeOrder(order(1, "CASH_ON_DELIVERY", phone, verifiedToken(phone, "CHECKOUT")))
                .getBody().get("invoiceNumber");

        assertThat(jdbc.queryForObject("""
                SELECT si.product_name FROM sale_items si JOIN sales s ON s.id = si.sale_id
                WHERE s.invoice_number = ?""", String.class, invoice)).isEqualTo(torch.getFullName());
    }

    @Test
    void staffProductFormFillsDefaultsAndKeepsPageAddressesUnique() {
        String name = "Loupe " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> form = new HashMap<>(Map.of("name", name, "categoryId", torch.getCategory().getId(),
                "buyingPrice", 100, "sellingPrice", 200, "stockQuantity", 1, "minimumStock", 1));

        Map<?, ?> first = adminPost("/api/products", form).getBody();
        assertThat(first.get("fullName")).isEqualTo(name);
        assertThat(first.get("showOnWebsite")).isEqualTo(true);
        String slug = (String) first.get("slug");
        assertThat(slug).isEqualTo(name.toLowerCase().replace(' ', '-'));

        // Same name again: the page address gets a number instead of clashing.
        assertThat(adminPost("/api/products", form).getBody().get("slug")).isEqualTo(slug + "-2");

        // Choosing an address another product uses, or an invalid one, is refused.
        form.put("slug", slug);
        assertThat(adminPost("/api/products", form).getStatusCode().value()).isEqualTo(400);
        form.put("slug", "Not A Slug!");
        assertThat(adminPost("/api/products", form).getStatusCode().value()).isEqualTo(400);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> storeProducts() {
        return http.get().uri("/api/store/products")
                .header("Authorization", "Bearer " + storeToken).retrieve().body(List.class);
    }

    private ResponseEntity<Map> adminPost(String path, Object body) {
        return http.post().uri(path).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(Map.class);
    }

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

    // --- phone verification ---------------------------------------------------------

    @Test
    void ordersNeedAVerifiedPhone() {
        String phone = newPhone();
        Map<String, Object> unverified = order(1, "CASH_ON_DELIVERY", phone, null);
        assertThat(placeOrder(unverified).getStatusCode().value()).isEqualTo(400);

        // A token for another number doesn't work either.
        Map<String, Object> otherNumber = order(1, "CASH_ON_DELIVERY", phone, verifiedToken(newPhone(), "CHECKOUT"));
        assertThat(placeOrder(otherNumber).getStatusCode().value()).isEqualTo(400);
        assertThat(stock()).isEqualTo(5);
    }

    @Test
    void verificationTokenWorksOnce() {
        String phone = newPhone();
        String token = verifiedToken(phone, "CHECKOUT");
        assertThat(placeOrder(order(1, "CASH_ON_DELIVERY", phone, token)).getStatusCode().value()).isEqualTo(201);
        assertThat(placeOrder(order(1, "CASH_ON_DELIVERY", phone, token)).getStatusCode().value()).isEqualTo(400);
        assertThat(stock()).isEqualTo(4);
    }

    @Test
    void wrongCodesLockTheCodeAndResendsAreLimited() {
        String phone = newPhone();
        assertThat(send(phone, "CHECKOUT")).containsEntry("status", "SENT");
        assertThat(send(phone, "CHECKOUT")).containsEntry("status", "TOO_SOON");

        String code = sentCodes.get(phone);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThat(check(phone, "CHECKOUT", wrong)).containsEntry("status", "INVALID");
        }
        // Five wrong guesses: even the right code no longer works.
        assertThat(check(phone, "CHECKOUT", code)).containsEntry("status", "INVALID");
    }

    @Test
    void checkoutCodeCannotSignIn() {
        String token = verifiedToken(newPhone(), "CHECKOUT");
        assertThat(signIn(token, "Someone").getStatusCode().value()).isEqualTo(400);
    }

    // --- orders -------------------------------------------------------------------------

    @Test
    void cashOnDeliveryIsACreditSaleThatStaffMarkPaid() {
        String phone = newPhone();
        ResponseEntity<Map> response = placeOrder(order(2, "CASH_ON_DELIVERY", phone, verifiedToken(phone, "CHECKOUT")));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsEntry("status", "PLACED").containsEntry("paymentMethod", "CASH_ON_DELIVERY")
                .containsEntry("subtotal", 5000.00).containsEntry("deliveryFee", 450.00)
                .containsEntry("grandTotal", 5450.00);
        assertThat(stock()).isEqualTo(3);

        Map<String, Object> sale = jdbc.queryForMap("""
                SELECT s.payment_method, s.delivery_fee, s.created_by, s.customer_id, c.contact_number
                FROM sales s JOIN customers c ON c.id = s.customer_id
                WHERE s.invoice_number = ?""", body.get("invoiceNumber"));
        assertThat(sale.get("payment_method")).isEqualTo("CREDIT");
        assertThat((BigDecimal) sale.get("delivery_fee")).isEqualByComparingTo("450.00");
        assertThat((String) sale.get("created_by")).startsWith("store-");
        assertThat(sale.get("contact_number")).isEqualTo(phone);
        long customerId = ((Number) sale.get("customer_id")).longValue();
        assertThat(outstanding(customerId)).isEqualByComparingTo("5450.00");

        // Courier brings the cash: staff record it in the POS as usual.
        assertThat(status(HttpMethod.POST, "/api/customers/" + customerId + "/payments", cashierToken,
                Map.of("amount", 5450, "paymentMethod", "CASH", "description", "COD collected"))).isIn(200, 201);
        assertThat(outstanding(customerId)).isEqualByComparingTo("0.00");
    }

    @Test
    void bankTransferIsNotAnOnlineOption() {
        String phone = newPhone();
        assertThat(placeOrder(order(1, "BANK_TRANSFER", phone, verifiedToken(phone, "CHECKOUT")))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(stock()).isEqualTo(5);
    }

    @Test
    void retriedOrderIsNotPlacedTwice() {
        String phone = newPhone();
        Map<String, Object> request = order(1, "CASH_ON_DELIVERY", phone, verifiedToken(phone, "CHECKOUT"));

        String first = (String) placeOrder(request).getBody().get("invoiceNumber");
        String second = (String) placeOrder(request).getBody().get("invoiceNumber");

        assertThat(second).isEqualTo(first);
        assertThat(stock()).isEqualTo(4);
    }

    @Test
    void cardOrdersNeedAUniqueVerifiedPaymentReference() {
        String phone = newPhone();
        assertThat(placeOrder(order(1, "CARD", phone, verifiedToken(phone, "CHECKOUT"))).getStatusCode().value()).isEqualTo(400);

        String reference = "OP-" + UUID.randomUUID();
        Map<String, Object> paid = order(1, "CARD", phone, verifiedToken(phone, "CHECKOUT"));
        paid.put("paymentReference", reference);
        ResponseEntity<Map> response = placeOrder(paid);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT payment_method FROM sales WHERE invoice_number = ?",
                String.class, response.getBody().get("invoiceNumber"))).isEqualTo("CARD");

        Map<String, Object> reused = order(1, "CARD", phone, verifiedToken(phone, "CHECKOUT"));
        reused.put("paymentReference", reference);
        assertThat(placeOrder(reused).getStatusCode().value()).isEqualTo(400);
        assertThat(stock()).isEqualTo(4);
    }

    @Test
    void outOfStockOrderLeavesNothingBehind() {
        String phone = newPhone();
        String token = verifiedToken(phone, "CHECKOUT");
        int ordersBefore = count("online_orders");
        int customersBefore = count("customers");

        ResponseEntity<Map> response = placeOrder(order(6, "CASH_ON_DELIVERY", phone, token));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat((String) response.getBody().get("message")).contains("Insufficient stock");
        assertThat(stock()).isEqualTo(5);
        assertThat(count("online_orders")).isEqualTo(ordersBefore);
        assertThat(count("customers")).isEqualTo(customersBefore);
        // The token wasn't spent, so the customer can fix the cart and retry.
        assertThat(placeOrder(order(1, "CASH_ON_DELIVERY", phone, token)).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void invalidOrderIsRejectedWithoutDetails() {
        String phone = newPhone();
        Map<String, Object> request = order(1, "CASH_ON_DELIVERY", phone, verifiedToken(phone, "CHECKOUT"));
        request.remove("addressLine1");
        ResponseEntity<Map> response = placeOrder(request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("message", "Invalid order details.");
        assertThat(stock()).isEqualTo(5);
    }

    @Test
    void existingShopCustomerIsReusedAndNotModified() {
        String phone = newPhone();
        Customer existing = shopCustomer("Shop Regular", "+94" + phone.substring(1));
        int customersBefore = count("customers");

        ResponseEntity<Map> response = placeOrder(order(1, "CASH_ON_DELIVERY", phone, verifiedToken(phone, "CHECKOUT")));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(count("customers")).isEqualTo(customersBefore);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM sales WHERE invoice_number = ?", Long.class,
                response.getBody().get("invoiceNumber"))).isEqualTo(existing.getId());
        assertThat(customerRepository.findById(existing.getId()).orElseThrow().getName()).isEqualTo("Shop Regular");
    }

    @Test
    void inactiveShopCustomerCannotOrderOnCredit() {
        String phone = newPhone();
        Customer inactive = shopCustomer("Closed Account", phone);
        inactive.setActive(false);
        customerRepository.save(inactive);

        assertThat(placeOrder(order(1, "CASH_ON_DELIVERY", phone, verifiedToken(phone, "CHECKOUT")))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(stock()).isEqualTo(5);
    }

    @Test
    void trackingNeedsTheRightPhoneAndShowsCancelledWhenStaffDeleteTheSale() {
        String phone = newPhone();
        String invoice = (String) placeOrder(order(1, "CASH_ON_DELIVERY", phone, verifiedToken(phone, "CHECKOUT")))
                .getBody().get("invoiceNumber");

        assertThat(track(invoice, phone).getStatusCode().value()).isEqualTo(200);
        assertThat(track(invoice, newPhone()).getStatusCode().value()).isEqualTo(404);

        assertThat(status(HttpMethod.DELETE, "/api/sales/" + invoice, adminToken, null)).isIn(200, 204);
        assertThat(track(invoice, phone).getBody()).containsEntry("status", "CANCELLED");
    }

    // --- customer accounts ------------------------------------------------------------

    @Test
    void shopCustomerSigningUpIsLinkedToTheirExistingRecord() {
        String phone = newPhone();
        Customer existing = shopCustomer("Shop Regular", "+94 " + phone.substring(1));
        int customersBefore = count("customers");

        Map<String, Object> verified = verify(phone, "ACCOUNT");
        assertThat(verified).containsEntry("accountExists", false).containsEntry("existingCustomerName", "Shop Regular");

        ResponseEntity<Map> account = signIn((String) verified.get("verificationToken"), null);
        assertThat(account.getStatusCode().value()).isEqualTo(200);
        assertThat(account.getBody()).containsEntry("id", existing.getId().intValue())
                .containsEntry("name", "Shop Regular").containsEntry("phone", phone);
        assertThat(count("customers")).isEqualTo(customersBefore);

        assertThat(verify(phone, "ACCOUNT")).containsEntry("accountExists", true);
    }

    @Test
    void newCustomerSignUpNeedsAName() {
        String phone = newPhone();
        assertThat(signIn((String) verify(phone, "ACCOUNT").get("verificationToken"), " ").getStatusCode().value()).isEqualTo(400);

        ResponseEntity<Map> account = signIn((String) verify(phone, "ACCOUNT").get("verificationToken"), "Kamala Silva");
        assertThat(account.getStatusCode().value()).isEqualTo(200);
        assertThat(account.getBody()).containsEntry("name", "Kamala Silva").containsEntry("phone", phone);
    }

    @Test
    void signedInCustomerOrdersWithoutACodeAndSeesTheirOrders() {
        String phone = newPhone();
        long customerId = ((Number) signIn((String) verify(phone, "ACCOUNT").get("verificationToken"), "Ruwan Fernando")
                .getBody().get("id")).longValue();

        Map<String, Object> request = order(1, "CASH_ON_DELIVERY", phone, null);
        request.put("customerId", customerId);
        String invoice = (String) placeOrder(request).getBody().get("invoiceNumber");
        assertThat(invoice).startsWith("INV-");

        ResponseEntity<List> orders = http.get().uri("/api/store/customers/{id}/orders", customerId)
                .header("Authorization", "Bearer " + storeToken).retrieve().toEntity(List.class);
        assertThat(orders.getBody()).extracting(o -> ((Map<?, ?>) o).get("invoiceNumber")).containsExactly(invoice);

        // An account can't be used to order for someone else's number.
        Map<String, Object> otherPhone = order(1, "CASH_ON_DELIVERY", newPhone(), null);
        otherPhone.put("customerId", customerId);
        assertThat(placeOrder(otherPhone).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void newAccountsNeedAnEmailAndShopCustomersGetItAdded() {
        String newPhone = newPhone();
        assertThat(signIn((String) verify(newPhone, "ACCOUNT").get("verificationToken"), "No Email", null)
                .getStatusCode().value()).isEqualTo(400);

        String shopPhone = newPhone();
        Customer existing = shopCustomer("Shop Regular", shopPhone);
        ResponseEntity<Map> account = signIn((String) verify(shopPhone, "ACCOUNT").get("verificationToken"), null,
                "Regular@Example.com");
        assertThat(account.getBody()).containsEntry("id", existing.getId().intValue())
                .containsEntry("email", "regular@example.com");
        assertThat(customerRepository.findById(existing.getId()).orElseThrow().getEmail()).isEqualTo("regular@example.com");
    }

    @Test
    void customersCanUpdateTheirEmailAndCheckoutKnowsTheirLastAddress() {
        String phone = newPhone();
        long customerId = ((Number) signIn((String) verify(phone, "ACCOUNT").get("verificationToken"), "Dilani")
                .getBody().get("id")).longValue();

        ResponseEntity<Map> updated = http.patch().uri("/api/store/customers/{id}", customerId)
                .header("Authorization", "Bearer " + storeToken).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "dilani@example.com")).retrieve().toEntity(Map.class);
        assertThat(updated.getBody()).containsEntry("email", "dilani@example.com");

        Map<String, Object> request = order(1, "CASH_ON_DELIVERY", phone, null);
        request.put("customerId", customerId);
        placeOrder(request);

        Map<?, ?> profile = http.get().uri("/api/store/customers/{id}", customerId)
                .header("Authorization", "Bearer " + storeToken).retrieve().body(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) profile.get("lastDeliveryAddress");
        assertThat(address).containsEntry("addressLine1", "12 Temple Road")
                .containsEntry("district", "Ratnapura");
    }

    @Test
    void guestEmailFillsABlankButNeverOverwrites() {
        String blankPhone = newPhone();
        Customer blank = shopCustomer("No Email Yet", blankPhone);
        Map<String, Object> first = order(1, "CASH_ON_DELIVERY", blankPhone, verifiedToken(blankPhone, "CHECKOUT"));
        first.put("customerEmail", "first@example.com");
        placeOrder(first);
        assertThat(customerRepository.findById(blank.getId()).orElseThrow().getEmail()).isEqualTo("first@example.com");

        Map<String, Object> second = order(1, "CASH_ON_DELIVERY", blankPhone, verifiedToken(blankPhone, "CHECKOUT"));
        second.put("customerEmail", "someone-else@example.com");
        placeOrder(second);
        assertThat(customerRepository.findById(blank.getId()).orElseThrow().getEmail()).isEqualTo("first@example.com");
    }

    @Test
    void customerWithoutAnAccountCannotOrderById() {
        String phone = newPhone();
        Customer shopOnly = shopCustomer("No Account", phone);
        Map<String, Object> request = order(1, "CASH_ON_DELIVERY", phone, null);
        request.put("customerId", shopOnly.getId());
        assertThat(placeOrder(request).getStatusCode().value()).isEqualTo(400);
    }

    // --- helpers --------------------------------------------------------------------------

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

    private Customer shopCustomer(String name, String contactNumber) {
        return customerRepository.save(Customer.builder()
                .name(name).contactNumber(contactNumber).createdAt(LocalDateTime.now()).build());
    }

    private static String newPhone() {
        return "07" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
    }

    private Map<String, Object> send(String phone, String purpose) {
        return storePost("/api/store/verification/send", Map.of("phone", phone, "purpose", purpose)).getBody();
    }

    private Map<String, Object> check(String phone, String purpose, String code) {
        return storePost("/api/store/verification/check", Map.of("phone", phone, "purpose", purpose, "code", code)).getBody();
    }

    private Map<String, Object> verify(String phone, String purpose) {
        jdbc.update("UPDATE customer_otps SET created_at = created_at - interval '2 minutes' WHERE phone = ?", phone);
        assertThat(send(phone, purpose)).containsEntry("status", "SENT");
        Map<String, Object> result = check(phone, purpose, sentCodes.get(phone));
        assertThat(result).containsEntry("status", "VERIFIED");
        return result;
    }

    private String verifiedToken(String phone, String purpose) {
        return (String) verify(phone, purpose).get("verificationToken");
    }

    private ResponseEntity<Map> signIn(String token, String name) {
        return signIn(token, name, "customer@example.com");
    }

    private ResponseEntity<Map> signIn(String token, String name, String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("verificationToken", token);
        if (name != null) body.put("name", name);
        if (email != null) body.put("email", email);
        return storePost("/api/store/customers/sign-in", body);
    }

    private Map<String, Object> order(int quantity, String paymentMethod, String phone, String verificationToken) {
        Map<String, Object> request = new HashMap<>();
        request.put("requestId", UUID.randomUUID().toString());
        request.put("items", List.of(Map.of("barcode", torch.getBarcode(), "quantity", quantity)));
        request.put("paymentMethod", paymentMethod);
        if (verificationToken != null) request.put("verificationToken", verificationToken);
        request.put("deliveryFee", 450);
        request.put("customerName", "Nimal Perera");
        request.put("customerPhone", phone);
        request.put("addressLine1", "12 Temple Road");
        request.put("city", "Ratnapura");
        request.put("district", "Ratnapura");
        return request;
    }

    private ResponseEntity<Map> placeOrder(Map<String, Object> request) {
        return storePost("/api/store/orders", request);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> storePost(String path, Object body) {
        return http.post().uri(path).header("Authorization", "Bearer " + storeToken)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(Map.class);
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

    private BigDecimal outstanding(long customerId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN type = 'PAYMENT' THEN -amount ELSE amount END), 0)
                FROM customer_transactions WHERE customer_id = ?""", BigDecimal.class, customerId);
    }

    private int stock() {
        return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, torch.getId());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
