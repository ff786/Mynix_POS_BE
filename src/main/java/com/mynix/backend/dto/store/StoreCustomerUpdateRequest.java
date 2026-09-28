package com.mynix.backend.dto.store;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** What a customer may change on their website account. */
@Data
public class StoreCustomerUpdateRequest {

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;
}
