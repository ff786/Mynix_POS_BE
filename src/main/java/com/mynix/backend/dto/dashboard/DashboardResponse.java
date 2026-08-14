package com.mynix.backend.dto.dashboard;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DashboardResponse {

    private BigDecimal salesToday;

    private Long ordersToday;

    private Long totalProducts;

    private Long totalCategories;

    private Long lowStockProducts;

}