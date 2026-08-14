package com.mynix.backend.dto.sales;

import com.mynix.backend.model.PaymentMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SaleResponse {

    private String invoiceNumber;

    private BigDecimal grandTotal;

    private PaymentMethod paymentMethod;

    private LocalDateTime createdAt;

}