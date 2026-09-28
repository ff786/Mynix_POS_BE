package com.mynix.backend.dto.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StoreAddressRequest {

    @NotBlank
    @Size(max = 40)
    private String label;

    @NotBlank
    @Size(max = 200)
    private String addressLine1;

    @Size(max = 200)
    private String addressLine2;

    @NotBlank
    @Size(max = 100)
    private String city;

    @NotBlank
    @Size(max = 100)
    private String district;

    @Size(max = 20)
    private String postalCode;

    private boolean makeDefault;
}
