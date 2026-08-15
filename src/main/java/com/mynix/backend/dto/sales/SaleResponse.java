package com.mynix.backend.dto.sales;

import com.mynix.backend.model.PaymentMethod;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SaleResponse {

    private String invoiceNumber;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal grandTotal;
    private PaymentMethod paymentMethod;
    private LocalDateTime createdAt;
    private BigDecimal deliveryFee;

    private List<SaleItemResponse> items;

}