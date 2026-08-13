package com.mynix.backend.service.impl;

import com.mynix.backend.model.User;
import com.mynix.backend.model.UserRole;
import com.mynix.backend.repository.UserRepository;
import com.mynix.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User createAdmin() {

        if (userRepository.existsByUsername("admin")) {
            return userRepository.findByUsername("admin").get();
        }

        User admin = User.builder()
                .fullName("System Administrator")
                .username("admin")
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .role(UserRole.ADMIN)
                .active(true)
                .build();

        return userRepository.save(admin);
    }
}