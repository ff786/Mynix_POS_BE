package com.mynix.backend.service.impl;

import com.mynix.backend.dto.checkout.CheckoutItem;
import com.mynix.backend.dto.checkout.CheckoutRequest;
import com.mynix.backend.dto.checkout.CheckoutResponse;
import com.mynix.backend.dto.store.StoreOrderItem;
import com.mynix.backend.dto.store.StoreOrderRequest;
import com.mynix.backend.dto.store.StoreOrderResponse;
import com.mynix.backend.dto.store.StoreProductResponse;
import com.mynix.backend.exception.StoreNotFoundException;
import com.mynix.backend.model.Customer;
import com.mynix.backend.model.OnlineOrder;
import com.mynix.backend.model.OnlineOrderStatus;
import com.mynix.backend.model.OnlinePaymentMethod;
import com.mynix.backend.model.Product;
import com.mynix.backend.model.Sale;
import com.mynix.backend.repository.CustomerRepository;
import com.mynix.backend.repository.OnlineOrderRepository;
import com.mynix.backend.repository.ProductRepository;
import com.mynix.backend.repository.SaleRepository;
import com.mynix.backend.service.PosService;
import com.mynix.backend.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final OnlineOrderRepository onlineOrderRepository;
    private final PosService posService;

    @Override
    @Transactional(readOnly = true)
    public List<StoreProductResponse> getProducts() {

        return productRepository.findActiveWithCategory()
                .stream()
                .map(this::toStoreProduct)
                .toList();
    }

    /**
     * Places an online order as a normal POS sale: the POS checkout prices the
     * items from the database, takes stock atomically, issues the invoice
     * number and sends the customer's invoice SMS. The delivery details are
     * saved in the same transaction, so either everything happens or nothing.
     */
    @Override
    @Transactional
    public StoreOrderResponse placeOrder(StoreOrderRequest request) {

        // Retried request (network hiccup, double click): return the same order.
        Optional<OnlineOrder> existing = onlineOrderRepository.findByRequestId(request.getRequestId());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        String paymentReference = blankToNull(request.getPaymentReference());
        if (request.getPaymentMethod() == OnlinePaymentMethod.CARD) {
            if (paymentReference == null) {
                throw new RuntimeException("Card orders need the verified payment reference.");
            }
            if (onlineOrderRepository.existsByPaymentReference(paymentReference)) {
                throw new RuntimeException("This payment has already been recorded.");
            }
        } else if (paymentReference != null) {
            throw new RuntimeException("Cash on delivery orders have no payment reference.");
        }

        String phone = normalizeSriLankanMobile(request.getCustomerPhone());
        if (phone == null) {
            throw new RuntimeException("Enter a valid Sri Lankan mobile number.");
        }
        String customerName = request.getCustomerName().trim();

        Customer customer = findOrCreateCustomer(customerName, phone);

        CheckoutRequest checkout = new CheckoutRequest();
        checkout.setItems(request.getItems().stream().map(this::toCheckoutItem).toList());
        checkout.setPaymentMethod(request.getPaymentMethod().posPaymentMethod());
        checkout.setDiscount(BigDecimal.ZERO);
        checkout.setDeliveryFee(request.getDeliveryFee());
        checkout.setCustomerId(customer == null ? null : customer.getId());

        CheckoutResponse result = posService.checkout(checkout);

        Sale sale = saleRepository.findByInvoiceNumber(result.getInvoiceNumber()).orElseThrow();

        OnlineOrder order = onlineOrderRepository.save(OnlineOrder.builder()
                .sale(sale)
                .invoiceNumber(sale.getInvoiceNumber())
                .requestId(request.getRequestId())
                .paymentMethod(request.getPaymentMethod())
                .paymentReference(paymentReference)
                .status(OnlineOrderStatus.PLACED)
                .customerName(customerName)
                .customerPhone(phone)
                .customerEmail(blankToNull(request.getCustomerEmail()))
                .addressLine1(request.getAddressLine1().trim())
                .addressLine2(blankToNull(request.getAddressLine2()))
                .city(request.getCity().trim())
                .district(request.getDistrict().trim())
                .postalCode(blankToNull(request.getPostalCode()))
                .deliveryNotes(blankToNull(request.getDeliveryNotes()))
                .build());

        return toResponse(order);
    }

    /** Order tracking: the phone number must match, and a mismatch looks like "not found". */
    @Override
    @Transactional(readOnly = true)
    public StoreOrderResponse getOrder(String invoiceNumber, String phone) {

        String normalized = normalizeSriLankanMobile(phone);

        return onlineOrderRepository.findByInvoiceNumber(invoiceNumber)
                .filter(order -> order.getCustomerPhone().equals(normalized))
                .map(this::toResponse)
                .orElseThrow(() -> new StoreNotFoundException("Order not found."));
    }

    // --- helpers ---------------------------------------------------------------

    /**
     * Reuses the POS customer with this mobile number (in any common format)
     * without changing their details; creates one otherwise. An inactive POS
     * customer can't buy in the POS, so the sale is then recorded without one.
     */
    private Customer findOrCreateCustomer(String name, String phone) {

        String local = phone.substring(1); // 7XXXXXXXX
        for (String candidate : List.of(phone, "+94" + local, "94" + local, local)) {
            Optional<Customer> found = customerRepository.findByContactNumber(candidate);
            if (found.isPresent()) {
                return Boolean.TRUE.equals(found.get().getActive()) ? found.get() : null;
            }
        }

        return customerRepository.save(Customer.builder()
                .name(name)
                .contactNumber(phone)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** 07XXXXXXXX, 7XXXXXXXX, 947XXXXXXXX or +947XXXXXXXX → 07XXXXXXXX; null if not a mobile number. */
    static String normalizeSriLankanMobile(String input) {

        if (input == null) {
            return null;
        }
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.length() == 11 && digits.startsWith("947")) {
            digits = "0" + digits.substring(2);
        } else if (digits.length() == 9 && digits.startsWith("7")) {
            digits = "0" + digits;
        }
        return digits.matches("^07[0-9]{8}$") ? digits : null;
    }

    private CheckoutItem toCheckoutItem(StoreOrderItem item) {

        CheckoutItem checkoutItem = new CheckoutItem();
        checkoutItem.setBarcode(item.getBarcode().trim());
        checkoutItem.setQuantity(item.getQuantity());
        return checkoutItem;
    }

    private StoreProductResponse toStoreProduct(Product product) {

        return StoreProductResponse.builder()
                .id(product.getId())
                .barcode(product.getBarcode())
                .name(product.getName())
                .categoryId(product.getCategory().getId())
                .category(product.getCategory().getName())
                .price(product.getSellingPrice())
                .availableQuantity(Math.max(0, product.getStockQuantity()))
                .imageUrl(product.getImageUrl())
                .build();
    }

    private StoreOrderResponse toResponse(OnlineOrder order) {

        Sale sale = order.getSale();
        OnlineOrderStatus status = sale == null ? OnlineOrderStatus.CANCELLED : order.getStatus();

        StoreOrderResponse.StoreOrderResponseBuilder response = StoreOrderResponse.builder()
                .invoiceNumber(order.getInvoiceNumber())
                .status(status.name())
                .paymentMethod(order.getPaymentMethod().name())
                .customerName(order.getCustomerName())
                .city(order.getCity())
                .district(order.getDistrict())
                .placedAt(order.getCreatedAt());

        if (sale == null) {
            return response.items(List.of()).build();
        }

        return response
                .items(sale.getItems().stream()
                        .map(item -> StoreOrderResponse.Line.builder()
                                .name(item.getProductName())
                                .quantity(item.getQuantity())
                                .unitPrice(money(item.getUnitPrice()))
                                .lineTotal(money(item.getLineTotal()))
                                .build())
                        .toList())
                .subtotal(money(sale.getSubtotal()))
                .deliveryFee(money(sale.getDeliveryFee()))
                .grandTotal(money(sale.getGrandTotal()))
                .build();
    }

    /** Amounts always with two decimals, whether just saved or read back. */
    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
