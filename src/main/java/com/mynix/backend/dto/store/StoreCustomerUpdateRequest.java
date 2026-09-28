package com.mynix.backend.dto.store;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** What a customer may change on their website account (only the fields sent). */
@Data
public class StoreCustomerUpdateRequest {

    @Size(min = 1, max = 150)
    private String name;

    @Email
    @Size(max = 254)
    private String email;
}
