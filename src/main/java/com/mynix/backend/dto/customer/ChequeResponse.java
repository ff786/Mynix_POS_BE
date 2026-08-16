package com.mynix.backend.dto.customer;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class ChequeResponse {

    private Long id;

    private Long customerId;

    private String customerName;

    private BigDecimal amount;

    private String chequeNumber;

    private LocalDate chequeDate;

    private LocalDate receivedDate;

    private LocalDate depositDate;

    private String bankName;

    private String status;

    private String bounceReason;

    private String notes;
}