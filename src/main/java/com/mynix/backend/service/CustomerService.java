package com.mynix.backend.service;

import com.mynix.backend.dto.customer.CustomerRequest;
import com.mynix.backend.dto.customer.CustomerResponse;
import com.mynix.backend.dto.customer.PaymentRequest;
import com.mynix.backend.dto.customer.PaymentResponse;
import com.mynix.backend.dto.customer.CustomerTransactionResponse;

import java.util.List;

public interface CustomerService {

    CustomerResponse create(CustomerRequest request);

    CustomerResponse update(Long id, CustomerRequest request);

    CustomerResponse getById(Long id);

    List<CustomerResponse> getAll();

    List<CustomerResponse> search(String query);

    void deactivate(Long id);

    PaymentResponse recordPayment(
            Long customerId,
            PaymentRequest request
    );

    List<CustomerTransactionResponse> getTransactions(Long customerId);

}