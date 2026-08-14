package com.mynix.backend.dto.checkout;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckoutItem {

    @NotBlank
    private String barcode;

    @Min(1)
    private Integer quantity;
}