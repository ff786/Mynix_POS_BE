package com.mynix.backend.dto.sales;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SaleItemResponse {

    private String productName;

    private String barcode;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal lineTotal;
}