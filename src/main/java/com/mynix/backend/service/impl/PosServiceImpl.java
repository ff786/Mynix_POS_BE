package com.mynix.backend.service.impl;

import com.mynix.backend.dto.checkout.CheckoutItem;
import com.mynix.backend.dto.checkout.CheckoutRequest;
import com.mynix.backend.dto.checkout.CheckoutResponse;
import com.mynix.backend.dto.pos.PosProductResponse;
import com.mynix.backend.model.*;
import com.mynix.backend.repository.*;
import com.mynix.backend.service.PosService;
import com.mynix.backend.service.SmsService;
import com.mynix.backend.util.InvoiceNumberGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private final CustomerTransactionRepository customerTransactionRepository;
    private final ChequeRepository chequeRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;

    private final SmsService smsService;

    @Override
    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request) {
        // Validate customer/payment combination
        Customer customer = null;

        if (request.getCustomerId() != null) {

            customer = customerRepository
                    .findById(request.getCustomerId())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Customer not found."
                            )
                    );

            if (!customer.getActive()) {

                throw new RuntimeException(
                        "Customer is inactive."
                );
            }
        }
        if (request.getPaymentMethod() == PaymentMethod.CREDIT
                && customer == null) {

            throw new RuntimeException(
                    "Customer is required for credit sales."
            );
        }

        if (request.getPaymentMethod() == PaymentMethod.CHEQUE
                && customer == null) {

            throw new RuntimeException(
                    "Customer is required for cheque sales."
            );
        }

        // Calculate subtotal and build sale items
        BigDecimal subtotal = BigDecimal.ZERO;
        List<SaleItem> saleItems = new ArrayList<>();

        for (CheckoutItem item : request.getItems()) {
            Product product = productRepository.findByBarcode(item.getBarcode())
                    .orElseThrow(() -> new RuntimeException("Product not found : " + item.getBarcode()));

            if (!product.getActive()) {
                throw new RuntimeException(product.getName() + " is inactive.");
            }

            if (product.getStockQuantity() < item.getQuantity()) {
                throw new RuntimeException("Insufficient stock for " + product.getName() + ". Available : " + product.getStockQuantity());
            }

            BigDecimal lineTotal = product.getSellingPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            SaleItem saleItem = SaleItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .barcode(product.getBarcode())
                    .quantity(item.getQuantity())
                    .unitPrice(product.getSellingPrice())
                    .lineTotal(lineTotal)
                    .build();

            saleItems.add(saleItem);
        }

        // Discount and delivery fee
        BigDecimal discount = request.getDiscount() == null ? BigDecimal.ZERO : request.getDiscount();
        BigDecimal deliveryFee = request.getDeliveryFee() == null ? BigDecimal.ZERO : request.getDeliveryFee();

        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Discount cannot be negative.");
        }

        if (deliveryFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Delivery fee cannot be negative.");
        }

        if (discount.compareTo(subtotal) > 0) {
            throw new RuntimeException("Discount cannot be greater than subtotal.");
        }

        BigDecimal grandTotal = subtotal.subtract(discount).add(deliveryFee);

        // Create Sale
        Sale sale = Sale.builder()
            .invoiceNumber(invoiceNumberGenerator.generate())
            .publicInvoiceToken(
                    UUID.randomUUID()
                            .toString()
                            .replace("-", "")
            )
            .publicInvoiceExpiresAt(
                    LocalDateTime.now().plusDays(3)
            )
            .subtotal(subtotal)
            .discount(discount)
            .deliveryFee(deliveryFee)
            .grandTotal(grandTotal)
            .paymentMethod(request.getPaymentMethod())
            .customer(customer)
            .build();

        for (SaleItem item : saleItems) {
            item.setSale(sale);
        }

        sale.setItems(saleItems);
        saleRepository.save(sale);

        // Reduce stock
        for (CheckoutItem item : request.getItems()) {
            Product product = productRepository.findByBarcode(item.getBarcode()).orElseThrow();
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
            productRepository.save(product);
        }

        // CREDIT SALE & CHEQUE PAYMENT TRANSACTIONS
        if (request.getPaymentMethod() == PaymentMethod.CREDIT) {

            CustomerTransaction transaction =
                    CustomerTransaction.builder()
                            .customer(customer)
                            .sale(sale)
                            .type(
                                    CustomerTransactionType.CREDIT_SALE
                            )
                            .amount(grandTotal)
                            .description(
                                    "Credit sale - "
                                            + sale.getInvoiceNumber()
                            )
                            .build();

            customerTransactionRepository.save(
                    transaction
            );

        } else if (
                request.getPaymentMethod() ==
                        PaymentMethod.CHEQUE
        ) {

            /*
             * 1. The cheque sale is an outstanding
             *    customer receivable.
             */
            CustomerTransaction creditSale =
                    CustomerTransaction.builder()
                            .customer(customer)
                            .sale(sale)
                            .type(
                                    CustomerTransactionType.CREDIT_SALE
                            )
                            .amount(grandTotal)
                            .description(
                                    "Cheque sale - "
                                            + sale.getInvoiceNumber()
                            )
                            .build();

            customerTransactionRepository.save(
                    creditSale
            );

            /*
             * 2. Create the cheque tracking record.
             *
             * This does NOT reduce outstanding.
             */
            Cheque cheque =
                    Cheque.builder()
                            .customer(customer)
                            .amount(grandTotal)
                            .chequeNumber(
                                    "CHQ-" +
                                            sale.getInvoiceNumber()
                            )
                            .chequeDate(
                                    LocalDate.now()
                            )
                            .receivedDate(
                                    LocalDate.now()
                            )
                            .status(
                                    ChequeStatus.RECEIVED
                            )
                            .notes(
                                    "Cheque received for invoice "
                                            + sale.getInvoiceNumber()
                            )
                            .build();

            chequeRepository.save(cheque);

            /*
             * 3. Track the cheque activity in the
             *    customer transaction history.
             *
             * IMPORTANT:
             * CHEQUE_PAYMENT has ZERO effect on
             * customer outstanding.
             */
            CustomerTransaction chequeTransaction =
                    CustomerTransaction.builder()
                            .customer(customer)
                            .sale(sale)
                            .type(
                                    CustomerTransactionType.CHEQUE_PAYMENT
                            )
                            .amount(grandTotal)
                            .description(
                                    "Cheque received - "
                                            + sale.getInvoiceNumber()
                            )
                            .build();

            customerTransactionRepository.save(
                    chequeTransaction
            );
        }

        // Calculate current customer outstanding
        BigDecimal customerOutstanding = null;

        if (customer != null) {
            customerOutstanding = calculateOutstanding(customer.getId());
        }

        BigDecimal finalCustomerOutstanding =
                customerOutstanding;

        if (customer != null &&
                customer.getContactNumber() != null &&
                !customer.getContactNumber().isBlank() &&
                sale.getPublicInvoiceToken() != null &&
                !sale.getPublicInvoiceToken().isBlank()) {

            smsService.sendInvoiceSms(
                    customer,
                    sale,
                    finalCustomerOutstanding
            );
        }

        // Return response
        return CheckoutResponse.builder()
                .invoiceNumber(sale.getInvoiceNumber())
                .subtotal(sale.getSubtotal())
                .deliveryFee(sale.getDeliveryFee())
                .discount(sale.getDiscount())
                .grandTotal(sale.getGrandTotal())
                .paymentMethod(sale.getPaymentMethod().name())
                .customerId(customer != null ? customer.getId() : null)
                .customerName(customer != null ? customer.getName() : null)
                .customerOutstanding(customerOutstanding)
                .build();
    }

    // Calculate customer outstanding.
    private BigDecimal calculateOutstanding(Long customerId) {
        List<CustomerTransaction> transactions = customerTransactionRepository.findByCustomerId(customerId);
        BigDecimal outstanding = BigDecimal.ZERO;

        for (CustomerTransaction transaction : transactions) {
            if (transaction.getType() == CustomerTransactionType.CREDIT_SALE) {
                outstanding = outstanding.add(transaction.getAmount());
            } else if (transaction.getType() == CustomerTransactionType.PAYMENT) {
                outstanding = outstanding.subtract(transaction.getAmount());
            }
        }

        // Never expose a negative outstanding.
        if (outstanding.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }

        return outstanding;
    }

    @Override
    public PosProductResponse getProductByBarcode(String barcode) {
        Product product = productRepository.findByBarcode(barcode)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!product.getActive()) {
            throw new RuntimeException("Product is inactive.");
        }

        return map(product);
    }

    @Override
    public List<PosProductResponse> searchProducts(String query) {
        return productRepository.findByNameContainingIgnoreCaseAndActiveTrue(query)
                .stream()
                .map(this::map)
                .toList();
    }

    private PosProductResponse map(Product product) {
        return PosProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .barcode(product.getBarcode())
                .sellingPrice(product.getSellingPrice())
                .stockQuantity(product.getStockQuantity())
                .imageUrl(product.getImageUrl())
                .build();
    }
}