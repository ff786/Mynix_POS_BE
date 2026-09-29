package com.mynix.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Related products sold as options of one listing on the website (colours, sizes…). */
@Entity
@Table(name = "product_variant_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    /** What the options are called: Colour, Size, Grit… */
    @Builder.Default
    @Column(name = "option_name", nullable = false, length = 40)
    private String optionName = "Option";

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
