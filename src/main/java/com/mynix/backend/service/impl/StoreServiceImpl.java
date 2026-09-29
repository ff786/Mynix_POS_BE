package com.mynix.backend.service.impl;

import com.mynix.backend.dto.checkout.CheckoutItem;
import com.mynix.backend.dto.checkout.CheckoutRequest;
import com.mynix.backend.dto.checkout.CheckoutResponse;
import com.mynix.backend.dto.store.StoreOrderItem;
import com.mynix.backend.dto.store.StoreOrderRequest;
import com.mynix.backend.dto.store.StoreOrderResponse;
import com.mynix.backend.dto.store.StoreCustomerResponse;
import com.mynix.backend.dto.store.StoreCustomerUpdateRequest;
import com.mynix.backend.dto.store.StoreProductResponse;
import com.mynix.backend.dto.store.StoreSignInRequest;
import com.mynix.backend.dto.store.StoreVerificationRequest;
import com.mynix.backend.dto.store.StoreVerificationResponse;
import com.mynix.backend.exception.StoreNotFoundException;
import com.mynix.backend.model.Customer;
import com.mynix.backend.model.CustomerAccount;
import com.mynix.backend.model.OnlineOrder;
import com.mynix.backend.model.OnlineOrderStatus;
import com.mynix.backend.model.OnlinePaymentMethod;
import com.mynix.backend.model.PaymentMethod;
import com.mynix.backend.model.VerificationPurpose;
import com.mynix.backend.model.Product;
import com.mynix.backend.model.Sale;
import com.mynix.backend.repository.CustomerAccountRepository;
import com.mynix.backend.repository.CustomerRepository;
import com.mynix.backend.repository.OnlineOrderRepository;
import com.mynix.backend.repository.ProductRepository;
import com.mynix.backend.repository.SaleRepository;
import com.mynix.backend.service.PosService;
import com.mynix.backend.service.StoreService;
import com.mynix.backend.util.PhoneNumbers;
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
    private final CustomerAccountRepository customerAccountRepository;
    private final PhoneVerificationService phoneVerification;
    private final OnlineOrderSmsService orderSms;

    /** How long the invoice link in the order SMS keeps working. */
    private static final int INVOICE_LINK_DAYS = 60;

    private static final String ACCOUNT_UNAVAILABLE =
            "We can't take online orders for this number. Please contact us on WhatsApp.";

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

        if (request.getPaymentMethod() == OnlinePaymentMethod.BANK_TRANSFER) {
            throw new RuntimeException("Please choose cash on delivery or card.");
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
            throw new RuntimeException("Only card orders have a payment reference.");
        }

        // Only products listed on the website can be ordered through it.
        for (StoreOrderItem item : request.getItems()) {
            Product product = productRepository.findByBarcode(item.getBarcode().trim()).orElse(null);
            if (product == null || !product.getActive() || !product.getShowOnWebsite()) {
                throw new RuntimeException("One of the items in your cart is no longer available online. Please review your cart.");
            }
        }

        String phone = PhoneNumbers.normalizeMobile(request.getCustomerPhone());
        if (phone == null) {
            throw new RuntimeException("Enter a valid Sri Lankan mobile number.");
        }
        String customerName = request.getCustomerName().trim();

        // Either a signed-in customer account, or a guest who verified this
        // mobile number by SMS code just now.
        Customer customer;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                    .filter(c -> customerAccountRepository.existsById(c.getId()))
                    .filter(c -> phone.equals(PhoneNumbers.normalizeMobile(c.getContactNumber())))
                    .orElseThrow(() -> new RuntimeException("Please sign in again."));
            if (!Boolean.TRUE.equals(customer.getActive())) {
                throw new RuntimeException(ACCOUNT_UNAVAILABLE);
            }
        } else {
            String verifiedPhone = phoneVerification.consumeToken(request.getVerificationToken(), VerificationPurpose.CHECKOUT);
            if (!phone.equals(verifiedPhone)) {
                throw new RuntimeException("Please verify your mobile number.");
            }
            customer = findOrCreateCustomer(customerName, phone);
        }

        // Cash on delivery is a POS credit sale, which needs a customer.
        if (customer == null && request.getPaymentMethod().posPaymentMethod() == PaymentMethod.CREDIT) {
            throw new RuntimeException(ACCOUNT_UNAVAILABLE);
        }

        CheckoutRequest checkout = new CheckoutRequest();
        checkout.setItems(request.getItems().stream().map(this::toCheckoutItem).toList());
        checkout.setPaymentMethod(request.getPaymentMethod().posPaymentMethod());
        checkout.setDiscount(BigDecimal.ZERO);
        checkout.setDeliveryFee(request.getDeliveryFee());
        checkout.setCustomerId(customer == null ? null : customer.getId());

        // Online customers get the website order SMS below, not the shop invoice SMS.
        CheckoutResponse result = posService.checkout(checkout, false);

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

        orderSms.orderPlaced(order, sale.getPublicInvoiceToken(), sale.getGrandTotal());

        // Guests may give an email at checkout; keep it if the shop has none yet.
        String orderEmail = normalizeEmail(request.getCustomerEmail());
        if (customer != null && orderEmail != null && blankToNull(customer.getEmail()) == null) {
            customer.setEmail(orderEmail);
            customerRepository.save(customer);
        }

        return toResponse(order);
    }

    /** Order tracking: the phone number must match, and a mismatch looks like "not found". */
    @Override
    @Transactional(readOnly = true)
    public StoreOrderResponse getOrder(String invoiceNumber, String phone) {

        String normalized = PhoneNumbers.normalizeMobile(phone);

        return onlineOrderRepository.findByInvoiceNumber(invoiceNumber)
                .filter(order -> order.getCustomerPhone().equals(normalized))
                .map(this::toResponse)
                .orElseThrow(() -> new StoreNotFoundException("Order not found."));
    }

    @Override
    public StoreVerificationResponse sendVerificationCode(StoreVerificationRequest request) {

        String phone = requireMobile(request.getPhone());
        return StoreVerificationResponse.builder()
                .status(phoneVerification.sendCode(phone, request.getPurpose()).name())
                .build();
    }

    @Override
    @Transactional
    public StoreVerificationResponse checkVerificationCode(StoreVerificationRequest request) {

        String phone = requireMobile(request.getPhone());
        String token = phoneVerification.verifyCode(phone, request.getPurpose(), request.getCode());
        if (token == null) {
            return StoreVerificationResponse.builder().status("INVALID").build();
        }

        StoreVerificationResponse.StoreVerificationResponseBuilder response =
                StoreVerificationResponse.builder().status("VERIFIED").verificationToken(token);
        if (request.getPurpose() == VerificationPurpose.ACCOUNT) {
            Optional<Customer> customer = findCustomerByPhone(phone);
            response.accountExists(customer.map(c -> customerAccountRepository.existsById(c.getId())).orElse(false))
                    .existingCustomerName(customer.map(Customer::getName).orElse(null));
        }
        return response.build();
    }

    /**
     * The verified number decides who the customer is. A shop customer with
     * this number gets their account linked to their existing POS record
     * (name kept as the shop has it); otherwise a POS customer is created.
     */
    @Override
    @Transactional
    public StoreCustomerResponse signIn(StoreSignInRequest request) {

        String phone = phoneVerification.consumeToken(request.getVerificationToken(), VerificationPurpose.ACCOUNT);
        if (phone == null) {
            throw new RuntimeException("Your code has expired. Please request a new one.");
        }

        Customer customer = findCustomerByPhone(phone).orElse(null);
        if (customer == null) {
            String name = blankToNull(request.getName());
            if (name == null) {
                throw new RuntimeException("Please enter your name.");
            }
            customer = customerRepository.save(Customer.builder()
                    .name(name)
                    .contactNumber(phone)
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build());
        } else if (!Boolean.TRUE.equals(customer.getActive())) {
            throw new RuntimeException(ACCOUNT_UNAVAILABLE);
        }

        boolean newAccount = !customerAccountRepository.existsById(customer.getId());
        String email = normalizeEmail(request.getEmail());
        if (newAccount && email == null && blankToNull(customer.getEmail()) == null) {
            throw new RuntimeException("Please enter your email address.");
        }
        if (email != null) {
            customer.setEmail(email);
            customer.setUpdatedAt(LocalDateTime.now());
            customerRepository.save(customer);
        }

        CustomerAccount account = customerAccountRepository.findById(customer.getId())
                .orElseGet(() -> CustomerAccount.builder().build());
        account.setCustomerId(customer.getId());
        account.setLastLoginAt(LocalDateTime.now());
        customerAccountRepository.save(account);

        return toCustomer(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public StoreCustomerResponse getCustomer(Long customerId) {
        return toCustomer(requireAccount(customerId));
    }

    @Override
    @Transactional
    public StoreCustomerResponse updateCustomer(Long customerId, StoreCustomerUpdateRequest request) {

        Customer customer = requireAccount(customerId);
        String name = blankToNull(request.getName());
        String email = normalizeEmail(request.getEmail());
        if (name == null && email == null) {
            throw new RuntimeException("Nothing to update.");
        }
        if (name != null) {
            customer.setName(name);
        }
        if (email != null) {
            customer.setEmail(email);
        }
        customer.setUpdatedAt(LocalDateTime.now());
        return toCustomer(customerRepository.save(customer));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoreOrderResponse> getCustomerOrders(Long customerId) {

        requireAccount(customerId);
        return onlineOrderRepository.findTop50BySale_Customer_IdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StoreOrderResponse getInvoice(String invoiceToken) {

        return onlineOrderRepository.findBySale_PublicInvoiceToken(invoiceToken)
                .filter(order -> order.getCreatedAt().isAfter(LocalDateTime.now().minusDays(INVOICE_LINK_DAYS)))
                .map(this::toResponse)
                .orElseThrow(() -> new StoreNotFoundException("Invoice not found."));
    }

    // --- helpers ---------------------------------------------------------------

    /**
     * Reuses the POS customer with this mobile number (in any common format)
     * without changing their details; creates one otherwise. Returns null for
     * an inactive POS customer (they can't buy in the POS either).
     */
    private Customer findOrCreateCustomer(String name, String phone) {

        Optional<Customer> existing = findCustomerByPhone(phone);
        if (existing.isPresent()) {
            return Boolean.TRUE.equals(existing.get().getActive()) ? existing.get() : null;
        }
        return customerRepository.save(Customer.builder()
                .name(name)
                .contactNumber(phone)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Optional<Customer> findCustomerByPhone(String phone) {

        // Prefers an active customer if the shop has the number more than once.
        return customerRepository.findByContactDigits(PhoneNumbers.digitVariants(phone)).stream().findFirst();
    }

    private static String requireMobile(String input) {

        String phone = PhoneNumbers.normalizeMobile(input);
        if (phone == null) {
            throw new RuntimeException("Enter a valid Sri Lankan mobile number.");
        }
        return phone;
    }

    /** An active POS customer with a website account, or "not found". */
    private Customer requireAccount(Long customerId) {

        return customerRepository.findById(customerId)
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .filter(c -> customerAccountRepository.existsById(c.getId()))
                .orElseThrow(() -> new StoreNotFoundException("Account not found."));
    }

    private StoreCustomerResponse toCustomer(Customer customer) {

        StoreCustomerResponse.Address lastAddress = onlineOrderRepository
                .findTop50BySale_Customer_IdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .findFirst()
                .map(o -> StoreCustomerResponse.Address.builder()
                        .addressLine1(o.getAddressLine1())
                        .addressLine2(o.getAddressLine2())
                        .city(o.getCity())
                        .district(o.getDistrict())
                        .postalCode(o.getPostalCode())
                        .build())
                .orElse(null);

        return StoreCustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .phone(PhoneNumbers.normalizeMobile(customer.getContactNumber()))
                .email(customer.getEmail())
                .lastDeliveryAddress(lastAddress)
                .build();
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
                .name(product.getFullName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .seoTitle(product.getSeoTitle())
                .seoDescription(product.getSeoDescription())
                .seoKeywords(product.getSeoKeywords())
                .imageAlt(product.getImageAlt())
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

    private static String normalizeEmail(String value) {
        String email = blankToNull(value);
        return email == null ? null : email.toLowerCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
