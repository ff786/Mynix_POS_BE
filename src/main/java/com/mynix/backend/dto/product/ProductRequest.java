package com.mynix.backend.dto.product;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {

    @NotBlank
    private String name;

    @NotNull
    private Long categoryId;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal buyingPrice;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal sellingPrice;

    @Min(0)
    private Integer stockQuantity;

    @Min(0)
    private Integer minimumStock;

    private String imageUrl;
}