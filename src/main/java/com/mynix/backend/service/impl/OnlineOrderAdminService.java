package com.mynix.backend.service.impl;

import com.mynix.backend.dto.customer.PaymentRequest;
import com.mynix.backend.dto.onlineorder.OnlineOrderResponse;
import com.mynix.backend.exception.StoreNotFoundException;
import com.mynix.backend.model.OnlineOrder;
import com.mynix.backend.model.OnlineOrderStatus;
import com.mynix.backend.model.OnlinePaymentMethod;
import com.mynix.backend.model.PaymentMethod;
import com.mynix.backend.model.Sale;
import com.mynix.backend.model.SaleItem;
import com.mynix.backend.repository.OnlineOrderRepository;
import com.mynix.backend.repository.ProductRepository;
import com.mynix.backend.service.CustomerService;
import com.mynix.backend.service.SaleService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Staff handling of website orders in the POS: pack, dispatch, deliver
 * (cash on delivery is then recorded as a customer payment, exactly like
 * the Customers screen), or cancel (sale removed, stock returned).
 */
@Service
@RequiredArgsConstructor
public class OnlineOrderAdminService {

    public enum Action { PACK, DISPATCH, DELIVER, CANCEL }

    private final OnlineOrderRepository onlineOrderRepository;
    private final ProductRepository productRepository;
    private final CustomerService customerService;
    private final SaleService saleService;
    private final EntityManager entityManager;
    private final OnlineOrderSmsService orderSms;

    @Transactional(readOnly = true)
    public List<OnlineOrderResponse> list(OnlineOrderStatus status) {

        return onlineOrderRepository.findTop300ByOrderByCreatedAtDesc()
                .stream()
                .filter(order -> status == null || effectiveStatus(order) == status)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OnlineOrderResponse get(String invoiceNumber) {
        return toResponse(find(invoiceNumber));
    }

    @Transactional
    public OnlineOrderResponse apply(String invoiceNumber, Action action) {

        OnlineOrder order = find(invoiceNumber);
        OnlineOrderStatus current = effectiveStatus(order);

        switch (action) {
            case PACK -> {
                require(current, EnumSet.of(OnlineOrderStatus.PLACED), "packed");
                order.setStatus(OnlineOrderStatus.PACKED);
            }
            case DISPATCH -> {
                require(current, EnumSet.of(OnlineOrderStatus.PLACED, OnlineOrderStatus.PACKED), "dispatched");
                order.setStatus(OnlineOrderStatus.DISPATCHED);
                orderSms.dispatched(order, order.getSale().getGrandTotal());
            }
            case DELIVER -> {
                require(current, EnumSet.of(OnlineOrderStatus.PLACED, OnlineOrderStatus.PACKED,
                        OnlineOrderStatus.DISPATCHED), "delivered");
                Sale sale = order.getSale();
                if (order.getPaymentMethod() == OnlinePaymentMethod.CASH_ON_DELIVERY) {
                    recordCashCollected(order, sale);
                }
                order.setStatus(OnlineOrderStatus.DELIVERED);
                orderSms.delivered(order, sale.getGrandTotal());
            }
            case CANCEL -> {
                require(current, EnumSet.of(OnlineOrderStatus.PLACED, OnlineOrderStatus.PACKED,
                        OnlineOrderStatus.DISPATCHED), "cancelled");
                cancel(order);
                orderSms.cancelled(order);
            }
        }

        order.setStatusUpdatedAt(LocalDateTime.now());
        order.setStatusUpdatedBy(currentUser());
        order.setUpdatedAt(LocalDateTime.now());
        return toResponse(onlineOrderRepository.save(order));
    }

    /** The courier brought the cash: settle the credit sale on the customer's account. */
    private void recordCashCollected(OnlineOrder order, Sale sale) {

        if (sale.getCustomer() == null) {
            return;
        }
        PaymentRequest payment = new PaymentRequest();
        payment.setAmount(sale.getGrandTotal());
        payment.setPaymentMethod(PaymentMethod.CASH);
        payment.setDescription("Cash on delivery collected - " + order.getInvoiceNumber());
        try {
            // No shop "payment received" SMS: the delivered SMS covers it.
            customerService.recordPayment(sale.getCustomer().getId(), payment, false);
        } catch (RuntimeException e) {
            throw new RuntimeException("Couldn't record the payment: " + e.getMessage()
                    + " If it was already recorded in Customers, check the customer's balance.");
        }
    }

    /** Returned or refused: the items go back on the shelf and the sale is removed. */
    private void cancel(OnlineOrder order) {

        Sale sale = order.getSale();
        for (SaleItem item : sale.getItems()) {
            if (item.getProduct() != null) {
                productRepository.incrementStock(item.getProduct().getId(), item.getQuantity());
            }
        }
        order.setSale(null);
        order.setStatus(OnlineOrderStatus.CANCELLED);
        onlineOrderRepository.saveAndFlush(order);
        entityManager.detach(sale);

        saleService.deleteSale(order.getInvoiceNumber());
    }

    private OnlineOrder find(String invoiceNumber) {
        return onlineOrderRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new StoreNotFoundException("Online order not found."));
    }

    private static OnlineOrderStatus effectiveStatus(OnlineOrder order) {
        // A sale deleted from the Sales screen also cancels the online order.
        return order.getSale() == null ? OnlineOrderStatus.CANCELLED : order.getStatus();
    }

    private static void require(OnlineOrderStatus current, Set<OnlineOrderStatus> allowed, String target) {
        if (!allowed.contains(current)) {
            throw new RuntimeException("A " + current.name().toLowerCase() + " order can't be marked " + target + ".");
        }
    }

    private static String currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    private OnlineOrderResponse toResponse(OnlineOrder order) {

        Sale sale = order.getSale();
        OnlineOrderResponse.OnlineOrderResponseBuilder response = OnlineOrderResponse.builder()
                .invoiceNumber(order.getInvoiceNumber())
                .status(effectiveStatus(order).name())
                .paymentMethod(order.getPaymentMethod().name())
                .placedAt(order.getCreatedAt())
                .statusUpdatedAt(order.getStatusUpdatedAt())
                .statusUpdatedBy(order.getStatusUpdatedBy())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .customerEmail(order.getCustomerEmail())
                .addressLine1(order.getAddressLine1())
                .addressLine2(order.getAddressLine2())
                .city(order.getCity())
                .district(order.getDistrict())
                .postalCode(order.getPostalCode())
                .deliveryNotes(order.getDeliveryNotes());

        if (sale == null) {
            return response.items(List.of()).build();
        }
        return response
                .customerId(sale.getCustomer() == null ? null : sale.getCustomer().getId())
                .items(sale.getItems().stream().map(item -> OnlineOrderResponse.Line.builder()
                        .name(item.getProductName())
                        .barcode(item.getBarcode())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .lineTotal(item.getLineTotal())
                        .build()).toList())
                .subtotal(sale.getSubtotal())
                .deliveryFee(sale.getDeliveryFee())
                .grandTotal(sale.getGrandTotal())
                .build();
    }
}
