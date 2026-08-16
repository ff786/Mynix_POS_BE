package com.mynix.backend.controller;

import com.mynix.backend.dto.customer.*;
import com.mynix.backend.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(
            @Valid @RequestBody CustomerRequest request
    ) {
        return customerService.create(request);
    }

    @PostMapping("/{id}/payments")
    public PaymentResponse recordPayment(
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequest request
    ) {

        return customerService.recordPayment(
                id,
                request
        );
    }
    @GetMapping("/{id}/transactions")
    public List<CustomerTransactionResponse> getTransactions(
            @PathVariable Long id
    ) {
        return customerService.getTransactions(id);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request
    ) {
        return customerService.update(id, request);
    }

    @GetMapping
    public List<CustomerResponse> getAll(
            @RequestParam(required = false) String search
    ) {

        if (search != null && !search.isBlank()) {
            return customerService.search(search);
        }

        return customerService.getAll();
    }
    @GetMapping("/search")
    public List<CustomerResponse> search(
            @RequestParam String query
    ) {
        return customerService.search(query);
    }


    @GetMapping("/{id}")
    public CustomerResponse getById(
            @PathVariable Long id
    ) {
        return customerService.getById(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @PathVariable Long id
    ) {
        customerService.deactivate(id);
    }
}