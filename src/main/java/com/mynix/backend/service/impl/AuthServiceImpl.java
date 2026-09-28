package com.mynix.backend.service.impl;

import com.mynix.backend.dto.auth.LoginRequest;
import com.mynix.backend.dto.auth.LoginResponse;
import com.mynix.backend.model.User;
import com.mynix.backend.repository.UserRepository;
import com.mynix.backend.security.JwtService;
import com.mynix.backend.service.AuthService;
import com.mynix.backend.exception.TooManyAttemptsException;
import com.mynix.backend.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttempts;

    @Override
    public LoginResponse login(LoginRequest request, String clientAddress) {

        String username = request.getUsername().toLowerCase();
        if (loginAttempts.isBlocked(username, clientAddress)) {
            throw new TooManyAttemptsException("Too many failed sign-in attempts. Please try again in 15 minutes.");
        }

        // Unknown user, wrong password and deactivated account all get the
        // same answer, so the response doesn't reveal which accounts exist.
        User user = userRepository.findByUsername(username)
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .filter(u -> passwordEncoder.matches(request.getPassword(), u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            loginAttempts.recordFailure(username, clientAddress);
            throw new RuntimeException("Invalid username or password");
        }
        loginAttempts.recordSuccess(username, clientAddress);

        String token = jwtService.generateToken(
                user.getUsername(),
                user.getRole().name()
        );

        return new LoginResponse(
                token,
                user.getUsername(),
                user.getRole().name()
        );
    }
}