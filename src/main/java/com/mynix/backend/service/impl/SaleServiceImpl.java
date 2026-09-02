package com.mynix.backend.service.impl;

import com.mynix.backend.dto.sales.SaleItemResponse;
import com.mynix.backend.dto.sales.SaleResponse;
import com.mynix.backend.dto.sales.SaleUpdateRequest;
import com.mynix.backend.model.CustomerTransaction;
import com.mynix.backend.model.CustomerTransactionType;
import com.mynix.backend.model.PaymentMethod;
import com.mynix.backend.model.Sale;
import com.mynix.backend.dto.sales.SaleItemUpdateRequest;
import com.mynix.backend.model.SaleItem;
import com.mynix.backend.repository.CustomerTransactionRepository;
import com.mynix.backend.repository.SaleItemRepository;
import com.mynix.backend.repository.SaleRepository;
import com.mynix.backend.service.SaleService;
import com.mynix.backend.service.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SaleServiceImpl implements SaleService {

    private final SaleItemRepository saleItemRepository;
    private final SaleRepository saleRepository;
    private final CustomerTransactionRepository transactionRepository;

    private final SmsService smsService;

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getAllSales() {

        return saleRepository.findAll()
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SaleResponse getSale(String invoiceNumber) {
        Sale sale = saleRepository
                .findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() ->
                        new RuntimeException("Sale not found")
                );
        return map(sale);
    }

    //Update the SALE

    @Override
    public SaleResponse updateSale(
            String invoiceNumber,
            SaleUpdateRequest request
    ) {
        Sale sale = saleRepository
                .findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() ->
                        new RuntimeException("Sale not found")
                );

        // Store old total for change detection
        BigDecimal oldTotal =
                sale.getGrandTotal() != null
                        ? sale.getGrandTotal()
                        : BigDecimal.ZERO;

        // Update sale items
        if (request.getItems() != null) {

            List<Long> requestedItemIds =
                request.getItems()
                    .stream()
                    .map(SaleItemUpdateRequest::getId)
                    .filter(id -> id != null)
                    .toList();


            /*Remove existing items that are no longer present.             */

            List<SaleItem> itemsToRemove =
                sale.getItems()
                    .stream()
                    .filter(existingItem ->
                        existingItem.getId() != null &&
                            !requestedItemIds.contains(
                                    existingItem.getId()
                            )
                     )
                    .toList();


            for (SaleItem item : itemsToRemove) {

                sale.getItems().remove(item);

                /*
                 * Because Sale.items uses:
                 *
                 * cascade = CascadeType.ALL
                 * orphanRemoval = true
                 *
                 * removing it from the collection will remove
                 * the corresponding database record.
                 */
            }
            /*
             * Do not allow a sale to have zero products.
             */
            if (sale.getItems().isEmpty()) {

                throw new RuntimeException(
                        "A sale must contain at least one product."
                );
            }
            /* Update remaining items.*/
            for (SaleItemUpdateRequest itemRequest :
                    request.getItems()) {

                if (itemRequest.getId() == null) {

                    throw new RuntimeException(
                            "Sale item ID is required."
                    );
                }


                SaleItem item =
                        sale.getItems()
                                .stream()
                                .filter(existingItem ->
                                        existingItem.getId()
                                                .equals(
                                                        itemRequest.getId()
                                                )
                                )
                                .findFirst()
                                .orElseThrow(() ->
                                        new RuntimeException(
                                                "Sale item does not belong to this sale."
                                        )
                                );

                //Update quantity
                if (itemRequest.getQuantity() != null) {

                    if (itemRequest.getQuantity() <= 0) {

                        throw new RuntimeException(
                                "Quantity must be greater than zero."
                        );
                    }

                    item.setQuantity(
                            itemRequest.getQuantity()
                    );
                }
                //Update unit price
                if (itemRequest.getUnitPrice() != null) {

                    if (itemRequest.getUnitPrice()
                            .compareTo(BigDecimal.ZERO) < 0) {

                        throw new RuntimeException(
                                "Unit price cannot be negative."
                        );
                    }

                    item.setUnitPrice(
                            itemRequest.getUnitPrice()
                    );
                }

                //Recalculate line total
                BigDecimal lineTotal =
                        item.getUnitPrice()
                                .multiply(
                                        BigDecimal.valueOf(
                                                item.getQuantity()
                                        )
                                );

                item.setLineTotal(lineTotal);
            }
        }

        // Recalculate subtotal
        BigDecimal subtotal = sale.getItems()
                .stream()
                .map(item ->
                        item.getLineTotal() != null
                                ? item.getLineTotal()
                                : BigDecimal.ZERO
                )
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );

        sale.setSubtotal(subtotal);

        // Update discount
        if (request.getDiscount() != null) {
            if (request.getDiscount()
                    .compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException(
                        "Discount cannot be negative."
                );
            }

            sale.setDiscount(request.getDiscount());
        }

        // Update delivery fee
        if (request.getDeliveryFee() != null) {
            if (request.getDeliveryFee()
                    .compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException(
                        "Delivery fee cannot be negative."
                );
            }

            sale.setDeliveryFee(request.getDeliveryFee());
        }

        // Update payment method
        if (request.getPaymentMethod() != null) {
            sale.setPaymentMethod(
                    PaymentMethod.valueOf(
                            request.getPaymentMethod()
                                    .trim()
                                    .toUpperCase()
                    )
            );
        }

        // Calculate grand total
        BigDecimal discount =
                sale.getDiscount() != null
                        ? sale.getDiscount()
                        : BigDecimal.ZERO;

        BigDecimal deliveryFee =
                sale.getDeliveryFee() != null
                        ? sale.getDeliveryFee()
                        : BigDecimal.ZERO;

        BigDecimal newTotal =
                subtotal
                        .subtract(discount)
                        .add(deliveryFee);

        // Prevent negative grand totals
        if (newTotal.compareTo(BigDecimal.ZERO) < 0) {
            newTotal = BigDecimal.ZERO;
        }

        sale.setGrandTotal(newTotal);

        // Update audit information
        String currentUser = getCurrentUsername();

        sale.setUpdatedBy(currentUser);
        sale.setUpdatedAt(LocalDateTime.now());

        // Save
        Sale savedSale = saleRepository.save(sale);

        // Detect total change
        boolean totalChanged =
                oldTotal.compareTo(newTotal) != 0;

        // Send SMS
        if (totalChanged && savedSale.getCustomer() != null) {
            smsService.sendSaleUpdateSms(
                    savedSale.getCustomer(),
                    savedSale,
                    oldTotal,
                    newTotal
            );
        }

        // Return updated sale
        return map(savedSale);
    }

    @Override
    public void deleteSale(String invoiceNumber) {

        Sale sale = saleRepository
                .findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() ->
                        new RuntimeException("Sale not found")
                );

        transactionRepository
                .findByCustomerId(
                        sale.getCustomer() != null
                                ? sale.getCustomer().getId()
                                : -1L
                )
                .stream()
                .filter(transaction ->
                        transaction.getSale() != null &&
                                transaction.getSale().getId()
                                        .equals(sale.getId())
                )
                .forEach(transactionRepository::delete);

        saleRepository.delete(sale);
    }

    private String getCurrentUsername() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            return "SYSTEM";
        }

        return authentication.getName();
    }

    private SaleResponse map(Sale sale) {

        List<SaleItemResponse> items =
                sale.getItems()
                    .stream()
                    .map(item ->
                        SaleItemResponse.builder()
                            .id(item.getId())
                            .productName(item.getProductName())
                            .barcode(item.getBarcode())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .lineTotal(item.getLineTotal())
                            .build()
                    )
                    .toList();

        Long customerId = null;
        String customerName = null;
        String customerContactNumber = null;
        BigDecimal customerOutstanding =
                BigDecimal.ZERO;

        if (sale.getCustomer() != null) {

            customerId =
                    sale.getCustomer().getId();

            customerName =
                    sale.getCustomer().getName();

            customerContactNumber =
                    sale.getCustomer().getContactNumber();

            customerOutstanding =
                    calculateOutstanding(customerId);
        }

        return SaleResponse.builder()
                .id(sale.getId())
                .invoiceNumber(
                        sale.getInvoiceNumber()
                )
                .subtotal(
                        sale.getSubtotal()
                )
                .discount(
                        sale.getDiscount()
                )
                .deliveryFee(
                        sale.getDeliveryFee()
                )
                .grandTotal(
                        sale.getGrandTotal()
                )
                .paymentMethod(
                        sale.getPaymentMethod()
                )
                .createdAt(
                        sale.getCreatedAt()
                )
                .createdBy(
                        sale.getCreatedBy()
                )
                .updatedAt(
                        sale.getUpdatedAt()
                )
                .updatedBy(
                        sale.getUpdatedBy()
                )
                .customerId(customerId)
                .customerName(customerName)
                .customerContactNumber(
                        customerContactNumber
                )
                .customerOutstanding(
                        customerOutstanding
                )
                .items(items)
                .build();
    }

    private BigDecimal calculateOutstanding(
            Long customerId
    ) {

        return transactionRepository
                .findByCustomerId(customerId)
                .stream()
                .map(this::transactionAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }

    private BigDecimal transactionAmount(
            CustomerTransaction transaction
    ) {

        if (transaction.getType()
                == CustomerTransactionType.CREDIT_SALE) {

            return transaction.getAmount();
        }

        if (transaction.getType()
                == CustomerTransactionType.PAYMENT) {

            return transaction.getAmount()
                    .negate();
        }

        return BigDecimal.ZERO;
    }
}