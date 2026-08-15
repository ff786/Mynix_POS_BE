package com.mynix.backend.dto.product;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private String barcode;
    private Long categoryId;
    private String category;
    private BigDecimal buyingPrice;
    private BigDecimal sellingPrice;
    private Integer stockQuantity;
    private Integer minimumStock;
    private String imageUrl;
    private Boolean active;
}