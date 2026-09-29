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
    /** Full product name (the short POS name stays in the POS). */
    private String name;
    private String slug;
    private String description;
    private String seoTitle;
    private String seoDescription;
    private String seoKeywords;
    private String imageAlt;
    /** Variable product: the group this product is an option of (null when standalone). */
    private Long variantGroupId;
    private String variantGroupName;
    private String variantOptionName;
    private String variantLabel;
    /** Photos and videos in display order ("shared" ones show for every option). */
    private java.util.List<com.mynix.backend.dto.media.MediaResponse> media;
    private Long categoryId;
    private String category;
    private BigDecimal price;
    private Integer availableQuantity;
    private String imageUrl;
}
