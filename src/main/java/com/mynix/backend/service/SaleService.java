package com.mynix.backend.service;

import com.mynix.backend.dto.sales.SaleResponse;

import java.util.List;

public interface SaleService {

    List<SaleResponse> getAllSales();

    SaleResponse getSale(String invoiceNumber);

}