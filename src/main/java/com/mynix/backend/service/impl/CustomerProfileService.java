package com.mynix.backend.service.impl;

import com.mynix.backend.dto.store.StoreAddressRequest;
import com.mynix.backend.dto.store.StoreAddressResponse;
import com.mynix.backend.exception.StoreNotFoundException;
import com.mynix.backend.model.Customer;
import com.mynix.backend.model.CustomerAddress;
import com.mynix.backend.repository.CustomerAccountRepository;
import com.mynix.backend.repository.CustomerAddressRepository;
import com.mynix.backend.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Website account extras: saved delivery addresses and closing the account.
 * Every call is scoped to the signed-in customer (the website passes the id
 * from its session); an address of another customer is "not found".
 */
@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    static final int MAX_ADDRESSES = 10;

    private final CustomerRepository customerRepository;
    private final CustomerAccountRepository accountRepository;
    private final CustomerAddressRepository addressRepository;

    @Transactional(readOnly = true)
    public List<StoreAddressResponse> addresses(Long customerId) {
        requireAccount(customerId);
        return addressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtAsc(customerId)
                .stream().map(CustomerProfileService::toResponse).toList();
    }

    @Transactional
    public StoreAddressResponse addAddress(Long customerId, StoreAddressRequest request) {
        requireAccount(customerId);
        long existing = addressRepository.countByCustomerId(customerId);
        if (existing >= MAX_ADDRESSES) {
            throw new RuntimeException("You can save up to " + MAX_ADDRESSES + " addresses.");
        }
        boolean makeDefault = request.isMakeDefault() || existing == 0;
        if (makeDefault) {
            addressRepository.clearDefault(customerId);
        }
        CustomerAddress address = CustomerAddress.builder().customerId(customerId).isDefault(makeDefault).build();
        apply(address, request);
        return toResponse(addressRepository.save(address));
    }

    @Transactional
    public StoreAddressResponse updateAddress(Long customerId, Long addressId, StoreAddressRequest request) {
        requireAccount(customerId);
        CustomerAddress address = requireAddress(customerId, addressId);
        if (request.isMakeDefault() && !address.getIsDefault()) {
            addressRepository.clearDefault(customerId);
            address = requireAddress(customerId, addressId);
            address.setIsDefault(true);
        }
        apply(address, request);
        address.setUpdatedAt(LocalDateTime.now());
        return toResponse(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(Long customerId, Long addressId) {
        requireAccount(customerId);
        CustomerAddress address = requireAddress(customerId, addressId);
        boolean wasDefault = address.getIsDefault();
        addressRepository.delete(address);
        addressRepository.flush();
        if (wasDefault) {
            addressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtAsc(customerId).stream().findFirst()
                    .ifPresent(next -> {
                        next.setIsDefault(true);
                        addressRepository.save(next);
                    });
        }
    }

    @Transactional
    public StoreAddressResponse makeDefault(Long customerId, Long addressId) {
        requireAccount(customerId);
        requireAddress(customerId, addressId);
        addressRepository.clearDefault(customerId);
        CustomerAddress address = requireAddress(customerId, addressId);
        address.setIsDefault(true);
        return toResponse(addressRepository.save(address));
    }

    /**
     * Closes the website account: the sign-in and saved addresses go; the
     * shop's customer record and purchase history stay in the POS.
     */
    @Transactional
    public void closeAccount(Long customerId) {
        requireAccount(customerId);
        addressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtAsc(customerId)
                .forEach(addressRepository::delete);
        accountRepository.deleteById(customerId);
    }

    private Customer requireAccount(Long customerId) {
        return customerRepository.findById(customerId)
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .filter(c -> accountRepository.existsById(c.getId()))
                .orElseThrow(() -> new StoreNotFoundException("Account not found."));
    }

    private CustomerAddress requireAddress(Long customerId, Long addressId) {
        return addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new StoreNotFoundException("Address not found."));
    }

    private static void apply(CustomerAddress address, StoreAddressRequest request) {
        address.setLabel(request.getLabel().trim());
        address.setAddressLine1(request.getAddressLine1().trim());
        address.setAddressLine2(blankToNull(request.getAddressLine2()));
        address.setCity(request.getCity().trim());
        address.setDistrict(request.getDistrict().trim());
        address.setPostalCode(blankToNull(request.getPostalCode()));
    }

    private static StoreAddressResponse toResponse(CustomerAddress a) {
        return StoreAddressResponse.builder()
                .id(a.getId())
                .label(a.getLabel())
                .addressLine1(a.getAddressLine1())
                .addressLine2(a.getAddressLine2())
                .city(a.getCity())
                .district(a.getDistrict())
                .postalCode(a.getPostalCode())
                .defaultAddress(Boolean.TRUE.equals(a.getIsDefault()))
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
