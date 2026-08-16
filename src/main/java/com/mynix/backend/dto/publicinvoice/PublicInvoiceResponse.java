package com.mynix.backend.dto.publicinvoice;

import com.mynix.backend.model.PaymentMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PublicInvoiceResponse {

    private String invoiceNumber;

    private LocalDateTime createdAt;

    private String customerName;

    private String customerContactNumber;

    private BigDecimal subtotal;

    private BigDecimal discount;

    private BigDecimal deliveryFee;

    private BigDecimal grandTotal;

    private PaymentMethod paymentMethod;

    private BigDecimal customerOutstanding;

    private List<PublicInvoiceItem> items;

    @Data
    @Builder
    public static class PublicInvoiceItem {

        private String productName;

        private Integer quantity;

        private BigDecimal unitPrice;

        private BigDecimal lineTotal;
    }
}