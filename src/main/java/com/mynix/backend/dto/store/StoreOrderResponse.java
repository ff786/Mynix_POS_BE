package com.mynix.backend.dto.store;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StoreOrderResponse {
    private String invoiceNumber;
    private String status;
    private String paymentMethod;
    private List<Line> items;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal grandTotal;
    private String customerName;
    private String city;
    private String district;
    private LocalDateTime placedAt;

    @Data
    @Builder
    public static class Line {
        private String name;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
    }
}
