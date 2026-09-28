package com.mynix.backend.controller;

import com.mynix.backend.dto.auth.LoginRequest;
import com.mynix.backend.dto.auth.LoginResponse;
import com.mynix.backend.service.AuthService;
import jakarta.validation.Valid;
import com.mynix.backend.util.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, ClientAddress.of(http));
    }
}