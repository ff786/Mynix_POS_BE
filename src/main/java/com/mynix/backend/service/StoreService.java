package com.mynix.backend.service;

import com.mynix.backend.dto.store.StoreOrderRequest;
import com.mynix.backend.dto.store.StoreOrderResponse;
import com.mynix.backend.dto.store.StoreCustomerResponse;
import com.mynix.backend.dto.store.StoreProductResponse;
import com.mynix.backend.dto.store.StoreSignInRequest;
import com.mynix.backend.dto.store.StoreVerificationRequest;
import com.mynix.backend.dto.store.StoreVerificationResponse;

import java.util.List;

/** What the website's server may do: read the catalogue and place/track online orders. */
public interface StoreService {

    List<StoreProductResponse> getProducts();

    StoreOrderResponse placeOrder(StoreOrderRequest request);

    StoreOrderResponse getOrder(String invoiceNumber, String phone);

    StoreVerificationResponse sendVerificationCode(StoreVerificationRequest request);

    StoreVerificationResponse checkVerificationCode(StoreVerificationRequest request);

    /** Signs in (or signs up) with a verified mobile number, linking the existing POS customer. */
    StoreCustomerResponse signIn(StoreSignInRequest request);

    StoreCustomerResponse getCustomer(Long customerId);

    List<StoreOrderResponse> getCustomerOrders(Long customerId);
}
