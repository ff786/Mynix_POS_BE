package com.mynix.backend;

import com.mynix.backend.dto.checkout.CheckoutItem;
import com.mynix.backend.dto.checkout.CheckoutRequest;
import com.mynix.backend.dto.checkout.CheckoutResponse;
import com.mynix.backend.model.Category;
import com.mynix.backend.model.PaymentMethod;
import com.mynix.backend.model.Product;
import com.mynix.backend.repository.CategoryRepository;
import com.mynix.backend.repository.ProductRepository;
import com.mynix.backend.service.PosService;
import com.mynix.backend.util.InvoiceNumberGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The till and the website will sell from the same stock at the same time.
 * These tests fire simultaneous checkouts at a real PostgreSQL.
 */
@IntegrationTest
class CheckoutConcurrencyTest {

    @Autowired PosService posService;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired InvoiceNumberGenerator invoiceNumberGenerator;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    void lastUnitIsSoldOnlyOnce() throws Exception {
        Product product = newProduct(1);

        List<Result> results = checkoutConcurrently(product.getBarcode(), 8);

        assertThat(results.stream().filter(Result::ok)).hasSize(1);
        assertThat(results.stream().filter(r -> !r.ok()))
                .hasSize(7)
                .allSatisfy(r -> assertThat(r.error()).contains("Insufficient stock"));
        assertThat(stockOf(product)).isZero();
        assertThat(salesFor(product)).isEqualTo(1);
    }

    @Test
    void concurrentSalesGetDistinctInvoiceNumbersAndCorrectStock() throws Exception {
        Product product = newProduct(20);

        List<Result> results = checkoutConcurrently(product.getBarcode(), 12);

        assertThat(results).allSatisfy(r -> assertThat(r.ok()).as(r.error()).isTrue());
        Set<String> invoices = new HashSet<>();
        results.forEach(r -> invoices.add(r.invoice()));
        assertThat(invoices).hasSize(12);
        assertThat(stockOf(product)).isEqualTo(8);
    }

    @Test
    void invoiceNumbersContinueFromExistingSalesAfterMigration() throws Exception {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        jdbc.update("DELETE FROM invoice_counters WHERE day = ?", LocalDate.now());
        jdbc.update("""
                INSERT INTO sales (invoice_number, subtotal, discount, grand_total, payment_method)
                VALUES (?, 0, 0, 0, 'CASH')
                """, "INV-" + today + "-0041");

        // Re-run the seeding part of V13 against this data.
        String migration = new ClassPathResource("db/migration/V13__create_invoice_counters.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        String seed = migration.substring(migration.indexOf("INSERT INTO invoice_counters"));
        jdbc.execute(seed.replace("GROUP BY 1;", "AND invoice_number LIKE 'INV-" + today + "-%' GROUP BY 1"));

        String next = transactions.execute(status -> invoiceNumberGenerator.generate());
        assertThat(next).isEqualTo("INV-" + today + "-0042");
    }

    @Test
    void failedCheckoutDoesNotUseUpAnInvoiceNumber() throws Exception {
        Product product = newProduct(0);
        String before = transactions.execute(status -> invoiceNumberGenerator.generate());

        List<Result> results = checkoutConcurrently(product.getBarcode(), 1);
        assertThat(results.getFirst().ok()).isFalse();

        String after = transactions.execute(status -> invoiceNumberGenerator.generate());
        assertThat(sequence(after)).isEqualTo(sequence(before) + 1);
    }

    // --- helpers ---------------------------------------------------------------

    record Result(boolean ok, String invoice, String error) {}

    private List<Result> checkoutConcurrently(String barcode, int buyers) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Result>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < buyers; i++) {
                Callable<Result> buyer = () -> {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken("test-cashier", null, List.of()));
                    start.await();
                    try {
                        CheckoutResponse response = posService.checkout(request(barcode));
                        return new Result(true, response.getInvoiceNumber(), null);
                    } catch (RuntimeException e) {
                        return new Result(false, null, e.getMessage());
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
                futures.add(pool.submit(buyer));
            }
            start.countDown();
            List<Result> results = new ArrayList<>();
            for (Future<Result> f : futures) {
                try {
                    results.add(f.get());
                } catch (ExecutionException e) {
                    throw new AssertionError(e.getCause());
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private CheckoutRequest request(String barcode) {
        CheckoutItem item = new CheckoutItem();
        item.setBarcode(barcode);
        item.setQuantity(1);
        CheckoutRequest request = new CheckoutRequest();
        request.setItems(List.of(item));
        request.setPaymentMethod(PaymentMethod.CASH);
        return request;
    }

    private Product newProduct(int stock) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.save(Category.builder().name("Test " + suffix).build());
        return productRepository.save(Product.builder()
                .name("Test torch " + suffix)
                .barcode("TEST-" + suffix)
                .category(category)
                .buyingPrice(new BigDecimal("1000.00"))
                .sellingPrice(new BigDecimal("1500.00"))
                .stockQuantity(stock)
                .build());
    }

    private int stockOf(Product product) {
        return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, product.getId());
    }

    private int salesFor(Product product) {
        return jdbc.queryForObject("SELECT count(*) FROM sale_items WHERE product_id = ?", Integer.class, product.getId());
    }

    private static int sequence(String invoice) {
        return Integer.parseInt(invoice.substring(invoice.lastIndexOf('-') + 1));
    }
}
