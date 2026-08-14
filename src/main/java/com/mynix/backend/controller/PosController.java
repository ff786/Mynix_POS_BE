package com.mynix.backend.controller;

import com.mynix.backend.dto.checkout.CheckoutRequest;
import com.mynix.backend.dto.checkout.CheckoutResponse;
import com.mynix.backend.dto.pos.PosProductResponse;
import com.mynix.backend.service.PosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
public class PosController {

    private final PosService posService;

    @PostMapping("/checkout")
    public CheckoutResponse checkout(
            @Valid @RequestBody CheckoutRequest request) {

        return posService.checkout(request);
    }
    @GetMapping("/product/{barcode}")
    public PosProductResponse getProduct(
            @PathVariable String barcode) {

        return posService.getProductByBarcode(barcode);
    }

    @GetMapping("/search")
    public List<PosProductResponse> searchProducts(
            @RequestParam String query) {

        return posService.searchProducts(query);
    }
}