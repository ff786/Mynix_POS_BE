package com.mynix.backend.controller;

import com.mynix.backend.dto.store.StoreAddressRequest;
import com.mynix.backend.dto.store.StoreAddressResponse;
import com.mynix.backend.exception.StoreNotFoundException;
import com.mynix.backend.service.impl.CustomerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Customers' saved delivery addresses, for staff (ADMIN / CASHIER): the same
 * address book customers manage in their website account.
 */
@RestController
@RequestMapping("/api/customers/{customerId}/addresses")
@RequiredArgsConstructor
public class CustomerAddressController {

    private final CustomerProfileService profileService;

    @GetMapping
    public List<StoreAddressResponse> list(@PathVariable Long customerId) {
        return profileService.addressesForStaff(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreAddressResponse add(@PathVariable Long customerId, @Valid @RequestBody StoreAddressRequest request) {
        return profileService.addForStaff(customerId, request);
    }

    @PutMapping("/{addressId}")
    public StoreAddressResponse update(@PathVariable Long customerId, @PathVariable Long addressId,
                                       @Valid @RequestBody StoreAddressRequest request) {
        return profileService.updateForStaff(customerId, addressId, request);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long customerId, @PathVariable Long addressId) {
        profileService.deleteForStaff(customerId, addressId);
    }

    @PostMapping("/{addressId}/default")
    public StoreAddressResponse makeDefault(@PathVariable Long customerId, @PathVariable Long addressId) {
        return profileService.makeDefaultForStaff(customerId, addressId);
    }

    @ExceptionHandler(StoreNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(StoreNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid() {
        return ResponseEntity.badRequest().body(Map.of("message", "Please fill in the address, city and district."));
    }
}
