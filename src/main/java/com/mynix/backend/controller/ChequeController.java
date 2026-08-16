package com.mynix.backend.controller;

import com.mynix.backend.dto.customer.ChequeRequest;
import com.mynix.backend.dto.customer.ChequeResponse;
import com.mynix.backend.dto.customer.ChequeStatusRequest;
import com.mynix.backend.service.ChequeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cheques")
@RequiredArgsConstructor
public class ChequeController {

    private final ChequeService chequeService;

    @PostMapping("/customer/{customerId}")
    public ChequeResponse create(
            @PathVariable Long customerId,
            @Valid @RequestBody ChequeRequest request
    ) {

        return chequeService.create(
                customerId,
                request
        );
    }

    @GetMapping
    public List<ChequeResponse> getAll() {

        return chequeService.getAll();
    }

    @GetMapping("/{id}")
    public ChequeResponse getById(
            @PathVariable Long id
    ) {

        return chequeService.getById(id);
    }

    @GetMapping("/customer/{customerId}")
    public List<ChequeResponse> getByCustomer(
            @PathVariable Long customerId
    ) {

        return chequeService.getByCustomer(
                customerId
        );
    }

    @PatchMapping("/{id}/status")
    public ChequeResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ChequeStatusRequest request
    ) {

        return chequeService.updateStatus(
                id,
                request
        );
    }
}