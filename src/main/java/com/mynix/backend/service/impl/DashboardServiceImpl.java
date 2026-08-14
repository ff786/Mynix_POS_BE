package com.mynix.backend.service.impl;

import com.mynix.backend.dto.dashboard.DashboardResponse;
import com.mynix.backend.repository.CategoryRepository;
import com.mynix.backend.repository.ProductRepository;
import com.mynix.backend.repository.SaleRepository;
import com.mynix.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public DashboardResponse getDashboard() {

        LocalDate today = LocalDate.now();

        LocalDateTime start = today.atStartOfDay();

        LocalDateTime end = today.plusDays(1).atStartOfDay();

        return DashboardResponse.builder()
                .salesToday(
                        saleRepository
                                .sumTodaySales(start, end)
                                .orElse(BigDecimal.ZERO)
                )
                .ordersToday(
                        saleRepository
                                .countByCreatedAtBetween(start, end)
                )
                .totalProducts(
                        productRepository.count()
                )
                .totalCategories(
                        categoryRepository.count()
                )
                .lowStockProducts(
                        productRepository.countLowStockProducts()
                )
                .build();
    }
}