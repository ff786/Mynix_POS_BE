package com.mynix.backend.controller;

import com.mynix.backend.dto.store.StoreOrderRequest;
import com.mynix.backend.dto.store.StoreOrderResponse;
import com.mynix.backend.dto.store.StoreCustomerResponse;
import com.mynix.backend.dto.store.StoreCustomerUpdateRequest;
import com.mynix.backend.dto.store.StoreProductResponse;
import com.mynix.backend.dto.store.StoreSignInRequest;
import com.mynix.backend.dto.store.StoreVerificationRequest;
import com.mynix.backend.dto.store.StoreVerificationResponse;
import com.mynix.backend.exception.StoreNotFoundException;
import com.mynix.backend.dto.newsletter.NewsletterSignupRequest;
import com.mynix.backend.service.StoreService;
import com.mynix.backend.service.impl.NewsletterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.List;
import java.util.Map;

/**
 * Online store API, used only by the website's server (role ONLINE_STORE, see
 * SecurityConfig). Browsers never call it directly.
 */
@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
@Validated
public class StoreController {

    private final StoreService storeService;
    private final NewsletterService newsletterService;

    @GetMapping("/products")
    public List<StoreProductResponse> products() {
        return storeService.getProducts();
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public StoreOrderResponse placeOrder(@Valid @RequestBody StoreOrderRequest request) {
        return storeService.placeOrder(request);
    }

    @GetMapping("/orders/{invoiceNumber}")
    public StoreOrderResponse order(
            @PathVariable @Pattern(regexp = "^INV-[0-9]{8}-[0-9]{1,6}$") String invoiceNumber,
            @RequestParam @NotBlank @Size(max = 20) String phone) {
        return storeService.getOrder(invoiceNumber, phone);
    }

    @GetMapping("/invoices/{token}")
    public StoreOrderResponse invoice(@PathVariable @Pattern(regexp = "^[a-f0-9]{32}$") String token) {
        return storeService.getInvoice(token);
    }

    @PostMapping("/verification/send")
    public StoreVerificationResponse sendCode(@Valid @RequestBody StoreVerificationRequest request) {
        return storeService.sendVerificationCode(request);
    }

    @PostMapping("/verification/check")
    public StoreVerificationResponse checkCode(@Valid @RequestBody StoreVerificationRequest request) {
        return storeService.checkVerificationCode(request);
    }

    @PostMapping("/customers/sign-in")
    public StoreCustomerResponse signIn(@Valid @RequestBody StoreSignInRequest request) {
        return storeService.signIn(request);
    }

    @GetMapping("/customers/{customerId}")
    public StoreCustomerResponse customer(@PathVariable Long customerId) {
        return storeService.getCustomer(customerId);
    }

    @PatchMapping("/customers/{customerId}")
    public StoreCustomerResponse updateCustomer(@PathVariable Long customerId,
                                                @Valid @RequestBody StoreCustomerUpdateRequest request) {
        return storeService.updateCustomer(customerId, request);
    }

    @GetMapping("/customers/{customerId}/orders")
    public List<StoreOrderResponse> customerOrders(@PathVariable Long customerId) {
        return storeService.getCustomerOrders(customerId);
    }

    @PostMapping("/newsletter")
    public Map<String, String> subscribe(@Valid @RequestBody NewsletterSignupRequest request) {
        return Map.of("status", newsletterService.subscribe(request.getEmail(), request.getVisitorHash()).name());
    }

    // Store-only error responses: clear 400/404s for the website, without
    // changing how the rest of the POS API reports errors.

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Map<String, String>> invalid() {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid order details."));
    }

    @ExceptionHandler(StoreNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(StoreNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }
}
