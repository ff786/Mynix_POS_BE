package com.mynix.backend.dto.customer;

import com.mynix.backend.model.CustomerTransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CustomerTransactionResponse {

    private Long id;

    private CustomerTransactionType type;

    private BigDecimal amount;

    private String description;

    private LocalDateTime createdAt;

    private Long saleId;

    private String invoiceNumber;
}
