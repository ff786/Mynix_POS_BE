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

    private BigDecimal grandTotal;

    private String paymentMethod;
}