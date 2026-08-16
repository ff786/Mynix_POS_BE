package com.mynix.backend.dto.customer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ChequeRequest {

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    @NotBlank
    @Size(max = 100)
    private String chequeNumber;

    @NotNull
    private LocalDate chequeDate;

    private LocalDate depositDate;

    @Size(max = 150)
    private String bankName;

    @Size(max = 500)
    private String notes;
}