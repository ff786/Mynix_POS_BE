package com.mynix.backend.dto.sales;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SaleUpdateRequest {

    private BigDecimal discount;
    private BigDecimal deliveryFee;
    private String paymentMethod;
    private List<SaleItemUpdateRequest> items;
}