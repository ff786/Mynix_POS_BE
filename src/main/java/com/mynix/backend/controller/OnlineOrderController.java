package com.mynix.backend.controller;

import com.mynix.backend.dto.onlineorder.OnlineOrderResponse;
import com.mynix.backend.exception.StoreNotFoundException;
import com.mynix.backend.model.OnlineOrderStatus;
import com.mynix.backend.service.impl.OnlineOrderAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Website orders for staff (ADMIN / CASHIER, see SecurityConfig). */
@RestController
@RequestMapping("/api/online-orders")
@RequiredArgsConstructor
public class OnlineOrderController {

    private final OnlineOrderAdminService service;

    @GetMapping
    public List<OnlineOrderResponse> list(@RequestParam(required = false) OnlineOrderStatus status) {
        return service.list(status);
    }

    @GetMapping("/{invoiceNumber}")
    public OnlineOrderResponse get(@PathVariable String invoiceNumber) {
        return service.get(invoiceNumber);
    }

    /** action: pack, dispatch, deliver or cancel. */
    @PostMapping("/{invoiceNumber}/{action}")
    public OnlineOrderResponse apply(@PathVariable String invoiceNumber, @PathVariable String action) {
        OnlineOrderAdminService.Action parsed;
        try {
            parsed = OnlineOrderAdminService.Action.valueOf(action.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown action: " + action);
        }
        return service.apply(invoiceNumber, parsed);
    }

    @ExceptionHandler(StoreNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(StoreNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }
}
