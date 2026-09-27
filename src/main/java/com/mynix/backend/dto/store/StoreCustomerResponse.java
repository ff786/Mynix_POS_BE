package com.mynix.backend.dto.store;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StoreCustomerResponse {
    private Long id;
    private String name;
    private String phone;
}
