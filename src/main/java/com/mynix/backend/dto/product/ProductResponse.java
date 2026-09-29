package com.mynix.backend.dto.product;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private String fullName;
    private String description;
    private Boolean showOnWebsite;
    private String slug;
    private String seoTitle;
    private String seoDescription;
    private String seoKeywords;
    private String imageAlt;
    private String barcode;
    private Long categoryId;
    private String category;
    private BigDecimal buyingPrice;
    private BigDecimal sellingPrice;
    private Integer stockQuantity;
    private Integer minimumStock;
    private String imageUrl;
    private Boolean active;
    private LocalDateTime createdAt;
}