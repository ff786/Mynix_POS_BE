package com.mynix.backend.service;

import com.mynix.backend.dto.customer.ChequeRequest;
import com.mynix.backend.dto.customer.ChequeResponse;
import com.mynix.backend.dto.customer.ChequeStatusRequest;

import java.util.List;

public interface ChequeService {

    ChequeResponse create(
            Long customerId,
            ChequeRequest request
    );

    ChequeResponse getById(Long id);

    List<ChequeResponse> getAll();

    List<ChequeResponse> getByCustomer(Long customerId);

    ChequeResponse updateStatus(
            Long id,
            ChequeStatusRequest request
    );
}