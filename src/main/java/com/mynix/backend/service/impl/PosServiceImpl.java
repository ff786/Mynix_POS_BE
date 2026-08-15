package com.mynix.backend.service.impl;

import com.mynix.backend.dto.checkout.CheckoutItem;
import com.mynix.backend.dto.checkout.CheckoutRequest;
import com.mynix.backend.dto.checkout.CheckoutResponse;
import com.mynix.backend.dto.pos.PosProductResponse;
import com.mynix.backend.model.Product;
import com.mynix.backend.model.Sale;
import com.mynix.backend.model.SaleItem;
import com.mynix.backend.repository.ProductRepository;
import com.mynix.backend.repository.SaleRepository;
import com.mynix.backend.service.PosService;
import com.mynix.backend.util.InvoiceNumberGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;

    @Override
    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request) {

        BigDecimal subtotal = BigDecimal.ZERO;

        List<SaleItem> saleItems = new ArrayList<>();

        for (CheckoutItem item : request.getItems()) {

            Product product = productRepository.findByBarcode(item.getBarcode())
                    .orElseThrow(() ->
                            new RuntimeException("Product not found : " + item.getBarcode()));

            if (!product.getActive()) {
                throw new RuntimeException(product.getName() + " is inactive.");
            }

            if (product.getStockQuantity() < item.getQuantity()) {
                throw new RuntimeException(
                        "Insufficient stock for "
                                + product.getName()
                                + ". Available : "
                                + product.getStockQuantity()
                );
            }

            BigDecimal lineTotal =
                    product.getSellingPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity()));

            subtotal = subtotal.add(lineTotal);

            SaleItem saleItem = SaleItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .barcode(product.getBarcode())
                    .quantity(item.getQuantity())
                    .unitPrice(product.getSellingPrice())
                    .lineTotal(lineTotal)
                    .build();

            saleItems.add(saleItem);
        }

        BigDecimal discount =
                request.getDiscount() == null
                        ? BigDecimal.ZERO
                        : request.getDiscount();

        BigDecimal deliveryFee = request.getDeliveryFee() != null
                ? request.getDeliveryFee()
                : BigDecimal.ZERO;
        // Discount cannot be negative
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException(
                    "Discount cannot be negative."
            );
        }
        // Discount cannot exceed subtotal
        if (discount.compareTo(subtotal) > 0) {
            throw new RuntimeException(
                    "Discount cannot be greater than subtotal."
            );
        }
        BigDecimal grandTotal = subtotal
                .subtract(discount)
                .add(deliveryFee);

        Sale sale = Sale.builder()
                .invoiceNumber(invoiceNumberGenerator.generate())
                .subtotal(subtotal)
                .discount(discount)
                .deliveryFee(deliveryFee)
                .grandTotal(grandTotal)
                .paymentMethod(request.getPaymentMethod())
                .build();

        for (SaleItem item : saleItems) {
            item.setSale(sale);
        }

        sale.setItems(saleItems);
        saleRepository.save(sale);

        for (CheckoutItem item : request.getItems()) {

            Product product = productRepository.findByBarcode(item.getBarcode()).orElseThrow();

            product.setStockQuantity(
                    product.getStockQuantity() - item.getQuantity()
            );

            productRepository.save(product);
        }
        return CheckoutResponse.builder()
                .invoiceNumber(sale.getInvoiceNumber())
                .subtotal(sale.getSubtotal())
                .deliveryFee(sale.getDeliveryFee())
                .discount(sale.getDiscount())
                .grandTotal(sale.getGrandTotal())
                .paymentMethod(sale.getPaymentMethod().name())
                .build();
    }

    @Override
    public PosProductResponse getProductByBarcode(String barcode) {

        Product product = productRepository.findByBarcode(barcode)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!product.getActive()) {
            throw new RuntimeException("Product is inactive.");
        }

        return map(product);
    }

    @Override
    public List<PosProductResponse> searchProducts(String query) {

        return productRepository
                .findByNameContainingIgnoreCaseAndActiveTrue(query)
                .stream()
                .map(this::map)
                .toList();
    }

    private PosProductResponse map(Product product) {

        return PosProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .barcode(product.getBarcode())
                .sellingPrice(product.getSellingPrice())
                .stockQuantity(product.getStockQuantity())
                .imageUrl(product.getImageUrl())
                .build();
    }
}