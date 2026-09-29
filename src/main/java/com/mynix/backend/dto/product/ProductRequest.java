package com.mynix.backend.dto.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {

    /** Short name for POS screens. */
    @NotBlank
    @Size(max = 150)
    private String name;

    /** Full name for the website and invoices; defaults to the short name. */
    @Size(max = 255)
    private String fullName;

    @Size(max = 10000)
    private String description;

    /** Null keeps the current setting (new products are shown). */
    private Boolean showOnWebsite;

    /** Product page address; generated from the full name when blank. */
    @Size(max = 120)
    @Pattern(regexp = "^$|^[a-z0-9]+(-[a-z0-9]+)*$",
            message = "Use lowercase letters, numbers and single hyphens, e.g. 10x-magnifying-loupe")
    private String slug;

    @Size(max = 70, message = "Keep the SEO title within 70 characters")
    private String seoTitle;

    @Size(max = 170, message = "Keep the SEO description within 170 characters")
    private String seoDescription;

    @Size(max = 255)
    private String seoKeywords;

    @Size(max = 160)
    private String imageAlt;

    /**
     * Photos and videos in display order. Null leaves them unchanged (and then
     * imageUrl works as before); a list replaces them and sets imageUrl.
     */
    @Valid
    @Size(max = 30, message = "A product can have up to 30 photos and videos")
    private java.util.List<com.mynix.backend.dto.media.MediaItemRequest> media;

    /** Variable product group, or null when this product stands alone. */
    private Long variantGroupId;

    /** This product's option in the group, e.g. "Black" (required with a group). */
    @Size(max = 60, message = "Keep the option within 60 characters")
    private String variantLabel;

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