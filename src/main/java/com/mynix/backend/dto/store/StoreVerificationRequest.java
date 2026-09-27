package com.mynix.backend.dto.store;

import com.mynix.backend.model.VerificationPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StoreVerificationRequest {

    @NotBlank
    @Pattern(regexp = "^[+0-9 ()-]{9,20}$")
    private String phone;

    @NotNull
    private VerificationPurpose purpose;

    /** Only when checking a code. */
    @Size(max = 10)
    private String code;
}
