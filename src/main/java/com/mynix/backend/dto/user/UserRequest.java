package com.mynix.backend.dto.user;

import com.mynix.backend.model.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserRequest {

    @NotBlank
    private String fullName;

    @NotBlank
    private String username;

    private String password;

    @NotNull
    private UserRole role;

}