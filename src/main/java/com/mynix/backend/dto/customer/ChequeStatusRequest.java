package com.mynix.backend.dto.customer;

import com.mynix.backend.model.ChequeStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ChequeStatusRequest {

    @NotNull
    private ChequeStatus status;

    private LocalDate depositDate;

    @Size(max = 255)
    private String bounceReason;

    @Size(max = 500)
    private String notes;
}