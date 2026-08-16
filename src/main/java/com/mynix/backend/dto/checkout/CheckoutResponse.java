package com.mynix.backend.dto.checkout;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CheckoutResponse {

    private String invoiceNumber;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal deliveryFee;
    private BigDecimal grandTotal;
    private String paymentMethod;
    private Long customerId;
    private String customerName;
    private BigDecimal customerOutstanding;
}