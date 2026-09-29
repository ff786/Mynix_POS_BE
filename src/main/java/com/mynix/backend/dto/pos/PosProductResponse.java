package com.mynix.backend.dto.pos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PosProductResponse {

    private Long id;

    private String name;

    /** Printed on receipts and invoices. */
    private String fullName;

    private String barcode;

    private BigDecimal sellingPrice;

    private Integer stockQuantity;

    private String imageUrl;
}