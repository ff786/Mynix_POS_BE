package com.mynix.backend.dto.store;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StoreCustomerResponse {
    private Long id;
    private String name;
    private String phone;
    private String email;
    /** From the customer's latest online order, to pre-fill checkout. */
    private Address lastDeliveryAddress;

    @Data
    @Builder
    public static class Address {
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String district;
        private String postalCode;
    }
}
