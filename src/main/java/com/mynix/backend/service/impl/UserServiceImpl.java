package com.mynix.backend.service.impl;

import com.mynix.backend.dto.user.UserRequest;
import com.mynix.backend.dto.user.UserResponse;
import com.mynix.backend.model.User;
import com.mynix.backend.model.UserRole;
import com.mynix.backend.repository.UserRepository;
import com.mynix.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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
                .passwordHash(
                        passwordEncoder.encode("Admin@123")
                )
                .role(UserRole.ADMIN)
                .active(true)
                .build();

        return userRepository.save(admin);
    }

    @Override
    public List<UserResponse> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    public UserResponse createUser(UserRequest request) {

        String username =
                request.getUsername()
                        .trim()
                        .toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException(
                    "Username already exists"
            );
        }

        if (request.getPassword() == null ||
                request.getPassword().isBlank()) {

            throw new RuntimeException(
                    "Password is required"
            );
        }

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .username(username)
                .passwordHash(
                        passwordEncoder.encode(
                                request.getPassword()
                        )
                )
                .role(request.getRole())
                .active(true)
                .build();

        user = userRepository.save(user);

        return map(user);
    }

    @Override
    public UserResponse updateUser(
            Long id,
            UserRequest request) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        )
                );

        String username =
                request.getUsername()
                        .trim()
                        .toLowerCase();

        userRepository.findByUsername(username)
                .ifPresent(existing -> {

                    if (!existing.getId().equals(id)) {

                        throw new RuntimeException(
                                "Username already exists"
                        );
                    }

                });

        user.setFullName(
                request.getFullName().trim()
        );

        user.setUsername(username);

        user.setRole(request.getRole());

        /*
         * Password is optional during editing.
         *
         * If Admin leaves it empty,
         * the existing password remains unchanged.
         */
        if (request.getPassword() != null &&
                !request.getPassword().isBlank()) {

            user.setPasswordHash(
                    passwordEncoder.encode(
                            request.getPassword()
                    )
            );
        }

        user.setUpdatedAt(LocalDateTime.now());

        user = userRepository.save(user);

        return map(user);
    }

    @Override
    public void deactivateUser(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        )
                );

        if ("admin".equalsIgnoreCase(
                user.getUsername())) {

            throw new RuntimeException(
                    "System administrator cannot be deactivated"
            );
        }

        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
    }

    private UserResponse map(User user) {

        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .role(user.getRole())
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}