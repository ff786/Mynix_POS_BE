package com.mynix.backend.dto.store;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StoreAddressResponse {
    private Long id;
    private String label;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String district;
    private String postalCode;
    private boolean defaultAddress;
}
