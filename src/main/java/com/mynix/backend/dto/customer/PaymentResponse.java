package com.mynix.backend.dto.customer;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PaymentResponse {

    private Long customerId;
    private String customerName;
    private BigDecimal paymentAmount;
    private String paymentMethod;
    private BigDecimal previousOutstanding;
    private BigDecimal remainingOutstanding;
    private String description;
}