package com.mynix.backend.dto.store;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Catalogue entry for the website's server. Deliberately excludes buying
 * price, minimum stock and timestamps.
 */
@Data
@Builder
public class StoreProductResponse {
    private Long id;
    private String barcode;
    private String name;
    private Long categoryId;
    private String category;
    private BigDecimal price;
    private Integer availableQuantity;
    private String imageUrl;
}
