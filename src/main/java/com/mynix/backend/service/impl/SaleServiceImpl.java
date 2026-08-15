package com.mynix.backend.service.impl;

import com.mynix.backend.dto.sales.SaleResponse;
import com.mynix.backend.model.Sale;
import com.mynix.backend.repository.SaleRepository;
import com.mynix.backend.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;

    @Override
    public List<SaleResponse> getAllSales() {
        return saleRepository.findAll()
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    public SaleResponse getSale(String invoiceNumber) {
        Sale sale = saleRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new RuntimeException("Sale not found"));

        return map(sale);
    }

    private SaleResponse map(Sale sale) {
        return SaleResponse.builder()
                .invoiceNumber(sale.getInvoiceNumber())
                .grandTotal(sale.getGrandTotal())
                .paymentMethod(sale.getPaymentMethod())
                .createdAt(sale.getCreatedAt())
                .deliveryFee(sale.getDeliveryFee())
                .subtotal(sale.getSubtotal())
                .discount(sale.getDiscount())
                .build();
    }
}