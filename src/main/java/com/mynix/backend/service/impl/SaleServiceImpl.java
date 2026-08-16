package com.mynix.backend.service.impl;

import com.mynix.backend.dto.sales.SaleItemResponse;
import com.mynix.backend.dto.sales.SaleResponse;
import com.mynix.backend.model.CustomerTransaction;
import com.mynix.backend.model.CustomerTransactionType;
import com.mynix.backend.model.Sale;
import com.mynix.backend.repository.CustomerTransactionRepository;
import com.mynix.backend.repository.SaleRepository;
import com.mynix.backend.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;
    private final CustomerTransactionRepository transactionRepository;

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
        List<SaleItemResponse> items = sale.getItems()
                .stream()
                .map(item -> SaleItemResponse.builder()
                        .productName(item.getProductName())
                        .barcode(item.getBarcode())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();

        Long customerId = null;
        String customerName = null;
        String customerContactNumber = null;
        BigDecimal customerOutstanding = BigDecimal.ZERO;

        if (sale.getCustomer() != null) {
            customerId = sale.getCustomer().getId();
            customerName = sale.getCustomer().getName();
            customerContactNumber = sale.getCustomer().getContactNumber();
            customerOutstanding = calculateOutstanding(customerId);
        }

        return SaleResponse.builder()
                .invoiceNumber(sale.getInvoiceNumber())
                .subtotal(sale.getSubtotal())
                .discount(sale.getDiscount())
                .deliveryFee(sale.getDeliveryFee())
                .grandTotal(sale.getGrandTotal())
                .paymentMethod(sale.getPaymentMethod())
                .createdAt(sale.getCreatedAt())
                .customerId(customerId)
                .customerName(customerName)
                .customerContactNumber(customerContactNumber)
                .customerOutstanding(customerOutstanding)
                .items(items)
                .build();
    }

    private BigDecimal calculateOutstanding(Long customerId) {
        return transactionRepository.findByCustomerId(customerId)
                .stream()
                .map(this::transactionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal transactionAmount(CustomerTransaction transaction) {
        if (transaction.getType() == CustomerTransactionType.CREDIT_SALE) {
            return transaction.getAmount();
        }

        return transaction.getAmount().negate();
    }
}