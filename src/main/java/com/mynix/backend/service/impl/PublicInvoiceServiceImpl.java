package com.mynix.backend.service.impl;

import com.mynix.backend.dto.publicinvoice.PublicInvoiceResponse;
import com.mynix.backend.model.CustomerTransaction;
import com.mynix.backend.model.CustomerTransactionType;
import com.mynix.backend.model.Sale;
import com.mynix.backend.repository.CustomerTransactionRepository;
import com.mynix.backend.repository.SaleRepository;
import com.mynix.backend.service.PublicInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicInvoiceServiceImpl
        implements PublicInvoiceService {

    private final SaleRepository saleRepository;
    private final CustomerTransactionRepository transactionRepository;

    @Override
    public PublicInvoiceResponse getInvoice(String token) {

        Sale sale = saleRepository
                .findByPublicInvoiceToken(token)
                .orElseThrow(() ->
                        new RuntimeException("Invoice not found.")
                );

        BigDecimal outstanding = BigDecimal.ZERO;

        if (sale.getCustomer() != null) {

            outstanding = transactionRepository
                    .findByCustomerId(
                            sale.getCustomer().getId()
                    )
                    .stream()
                    .map(this::transactionAmount)
                    .reduce(
                            BigDecimal.ZERO,
                            BigDecimal::add
                    );
        }

        var items = sale.getItems()
                .stream()
                .map(item ->
                        PublicInvoiceResponse.PublicInvoiceItem
                                .builder()
                                .productName(item.getProductName())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .lineTotal(item.getLineTotal())
                                .build()
                )
                .toList();

        return PublicInvoiceResponse.builder()
                .invoiceNumber(sale.getInvoiceNumber())
                .createdAt(sale.getCreatedAt())

                .customerName(
                        sale.getCustomer() != null
                                ? sale.getCustomer().getName()
                                : null
                )

                .customerContactNumber(
                        sale.getCustomer() != null
                                ? sale.getCustomer().getContactNumber()
                                : null
                )

                .subtotal(sale.getSubtotal())
                .discount(sale.getDiscount())
                .deliveryFee(sale.getDeliveryFee())
                .grandTotal(sale.getGrandTotal())
                .paymentMethod(sale.getPaymentMethod())
                .customerOutstanding(outstanding)
                .items(items)
                .build();
    }

    private BigDecimal transactionAmount(
            CustomerTransaction transaction
    ) {

        if (transaction.getType()
                == CustomerTransactionType.CREDIT_SALE) {

            return transaction.getAmount();
        }

        return transaction.getAmount().negate();
    }
}