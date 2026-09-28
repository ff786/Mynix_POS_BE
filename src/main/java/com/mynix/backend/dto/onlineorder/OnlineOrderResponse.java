package com.mynix.backend.dto.onlineorder;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** A website order as staff see it in the POS (everything needed to pack and deliver). */
@Data
@Builder
public class OnlineOrderResponse {
    private String invoiceNumber;
    private String status;
    private String paymentMethod;
    private LocalDateTime placedAt;
    private LocalDateTime statusUpdatedAt;
    private String statusUpdatedBy;

    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;

    private String addressLine1;
    private String addressLine2;
    private String city;
    private String district;
    private String postalCode;
    private String deliveryNotes;

    private List<Line> items;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal grandTotal;

    @Data
    @Builder
    public static class Line {
        private String name;
        private String barcode;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
    }
}
