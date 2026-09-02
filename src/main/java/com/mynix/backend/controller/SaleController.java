package com.mynix.backend.controller;

import com.mynix.backend.dto.sales.SaleResponse;
import com.mynix.backend.dto.sales.SaleUpdateRequest;
import com.mynix.backend.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
            @PathVariable String invoiceNumber
    ) {
        return saleService.getSale(invoiceNumber);
    }

    @PutMapping("/{invoiceNumber}")
    public SaleResponse updateSale(
            @PathVariable String invoiceNumber,
            @RequestBody SaleUpdateRequest request
    ) {
        return saleService.updateSale(
                invoiceNumber,
                request
        );
    }

    @DeleteMapping("/{invoiceNumber}")
    public ResponseEntity<Void> deleteSale(
            @PathVariable String invoiceNumber
    ) {
        saleService.deleteSale(invoiceNumber);

        return ResponseEntity.noContent().build();
    }
}