package com.mynix.backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String username;
    private String role;
    /** True after an emergency reset: the POS asks for a new password first. */
    private boolean mustChangePassword;

}