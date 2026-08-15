package com.mynix.backend.dto.user;

import com.mynix.backend.model.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {

    private Long id;

    private String fullName;

    private String username;

    private UserRole role;

    private Boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}