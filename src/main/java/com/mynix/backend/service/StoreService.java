package com.mynix.backend.service;

import com.mynix.backend.dto.store.StoreOrderRequest;
import com.mynix.backend.dto.store.StoreOrderResponse;
import com.mynix.backend.dto.store.StoreProductResponse;

import java.util.List;

/** What the website's server may do: read the catalogue and place/track online orders. */
public interface StoreService {

    List<StoreProductResponse> getProducts();

    StoreOrderResponse placeOrder(StoreOrderRequest request);

    StoreOrderResponse getOrder(String invoiceNumber, String phone);
}
