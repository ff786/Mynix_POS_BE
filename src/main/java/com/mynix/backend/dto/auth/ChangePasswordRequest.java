package com.mynix.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Enter the code from the SMS")
    @Size(max = 10)
    private String code;

    @NotBlank(message = "Enter a new password")
    @Size(max = 100)
    private String newPassword;
}
