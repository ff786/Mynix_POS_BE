package com.mynix.backend.dto.store;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StoreSignInRequest {

    /** From a verified ACCOUNT code. */
    @NotBlank
    @Size(max = 100)
    private String verificationToken;

    /** Needed only when the shop has no customer with this number yet. */
    @Size(max = 150)
    private String name;

    /** Required when creating the account; optional when signing in again. */
    @Email
    @Size(max = 254)
    private String email;
}
