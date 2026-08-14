package com.mynix.backend.service;

import com.mynix.backend.dto.checkout.CheckoutRequest;
import com.mynix.backend.dto.checkout.CheckoutResponse;
import com.mynix.backend.dto.pos.PosProductResponse;

import java.util.List;

public interface PosService {

    CheckoutResponse checkout(CheckoutRequest request);

    PosProductResponse getProductByBarcode(String barcode);

    List<PosProductResponse> searchProducts(String query);

}