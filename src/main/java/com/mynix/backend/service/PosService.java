package com.mynix.backend.service;

import com.mynix.backend.dto.checkout.CheckoutRequest;
import com.mynix.backend.dto.checkout.CheckoutResponse;
import com.mynix.backend.dto.pos.PosProductResponse;

import java.util.List;

public interface PosService {
    /* Checkout a list of items.
     *
     * @param request The checkout request.
     * @return The checkout response.
     */
    CheckoutResponse checkout(CheckoutRequest request);

    /**
     * Same checkout; sendInvoiceSms=false lets the online store send its own
     * order SMS instead of the shop invoice SMS.
     */
    CheckoutResponse checkout(CheckoutRequest request, boolean sendInvoiceSms);
    /* Get a product by its barcode.
     *
     * @param barcode The barcode of the product to retrieve.
     * @return The product if found, otherwise throws an exception.
     */
    PosProductResponse getProductByBarcode(String barcode);
    List<PosProductResponse> searchProducts(String query);

}