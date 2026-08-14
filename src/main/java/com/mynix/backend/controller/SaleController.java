package com.mynix.backend.controller;

import com.mynix.backend.dto.sales.SaleResponse;
import com.mynix.backend.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @GetMapping
    public List<SaleResponse> getSales() {

        return saleService.getAllSales();

    }

    @GetMapping("/{invoiceNumber}")
    public SaleResponse getSale(
            @PathVariable String invoiceNumber) {

        return saleService.getSale(invoiceNumber);

    }

}