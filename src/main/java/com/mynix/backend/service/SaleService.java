package com.mynix.backend.service;

import com.mynix.backend.dto.sales.SaleResponse;
import com.mynix.backend.dto.sales.SaleUpdateRequest;

import java.util.List;

public interface SaleService {

    List<SaleResponse> getAllSales();

    SaleResponse getSale(String invoiceNumber);

    SaleResponse updateSale(
            String invoiceNumber,
            SaleUpdateRequest request
    );

    void deleteSale(String invoiceNumber);
}