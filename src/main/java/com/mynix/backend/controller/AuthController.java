package com.mynix.backend.controller;

import com.mynix.backend.dto.auth.LoginRequest;
import com.mynix.backend.dto.auth.LoginResponse;
import com.mynix.backend.dto.auth.ChangePasswordRequest;
import com.mynix.backend.model.User;
import com.mynix.backend.repository.UserRepository;
import com.mynix.backend.service.AuthService;
import com.mynix.backend.service.StaffPasswordService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
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
    private final StaffPasswordService staffPasswordService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, ClientAddress.of(http));
    }

    /** Step 1 of a password change: an SMS code to the shop phone. */
    @PostMapping("/password/code")
    public Map<String, String> sendPasswordCode() {
        return Map.of("sentTo", staffPasswordService.sendCode(currentUser()));
    }

    /** Step 2: the code and the new password. */
    @PostMapping("/password")
    public Map<String, String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        staffPasswordService.changePassword(currentUser(), request.getCode(), request.getNewPassword());
        return Map.of("message", "Password changed.");
    }

    /** /api/auth is open for signing in, so these check the signed-in user themselves. */
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        }
        return userRepository.findByUsername(auth.getName())
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again."));
    }
}