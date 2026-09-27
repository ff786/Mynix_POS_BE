package com.mynix.backend.dto.store;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StoreOrderItem {

    @NotBlank
    @Size(max = 50)
    private String barcode;

    @NotNull
    @Min(1)
    @Max(50)
    private Integer quantity;
}
