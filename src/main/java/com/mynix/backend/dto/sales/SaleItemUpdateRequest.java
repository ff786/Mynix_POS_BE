package com.mynix.backend.dto.sales;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SaleItemUpdateRequest {

    private Long id;

    private Integer quantity;

    private BigDecimal unitPrice;
}