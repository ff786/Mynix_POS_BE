package com.mynix.backend.model;

import jakarta.persistence.*;
import com.mynix.backend.util.Slugs;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Short name for POS screens. */
    @Column(nullable = false, length = 150)
    private String name;

    /** Full name: shown on the website and printed on invoices. */
    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "show_on_website", nullable = false)
    private Boolean showOnWebsite = true;

    /** Product page address on the website: /products/{slug}. */
    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "seo_title", length = 70)
    private String seoTitle;

    @Column(name = "seo_description", length = 170)
    private String seoDescription;

    @Column(name = "seo_keywords", length = 255)
    private String seoKeywords;

    @Column(name = "image_alt", length = 160)
    private String imageAlt;

    /** Set when this product is one option of a variable product (colour, size…). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_group_id")
    private ProductVariantGroup variantGroup;

    /** This product's option within its group, e.g. "Black". */
    @Column(name = "variant_label", length = 60)
    private String variantLabel;

    /** Photos and videos in display order (the first photo is also imageUrl). */
    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @org.hibernate.annotations.BatchSize(size = 100)
    private java.util.List<ProductMedia> media = new java.util.ArrayList<>();

    @Column(nullable = false, unique = true, length = 50)
    private String barcode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "buying_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal buyingPrice;

    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Builder.Default
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    @Builder.Default
    @Column(name = "minimum_stock", nullable = false)
    private Integer minimumStock = 5;

    @Column(name = "image_url")
    private String imageUrl;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Safety net for saves that skip ProductService: the barcode keeps the address unique. */
    @PrePersist
    void fillWebsiteDefaults() {
        if (fullName == null || fullName.isBlank()) {
            fullName = name;
        }
        if (slug == null || slug.isBlank()) {
            slug = Slugs.of(fullName + " " + barcode);
        }
    }
}
