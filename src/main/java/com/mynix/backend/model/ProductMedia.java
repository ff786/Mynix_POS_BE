package com.mynix.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One photo or video of a product, in display order. */
@Entity
@Table(name = "product_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductMediaType type;

    /** Uploaded file in media storage (R2); null for links. */
    @Column(name = "storage_key", length = 200)
    private String storageKey;

    /** Image link; null for uploads (their address comes from the storage key). */
    @Column(length = 1000)
    private String url;

    @Column(name = "youtube_id", length = 20)
    private String youtubeId;

    @Column(name = "alt_text", length = 160)
    private String altText;

    /** Shown for every option of a variable product on the website. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean shared = false;

    @Builder.Default
    @Column(nullable = false)
    private Integer position = 0;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
