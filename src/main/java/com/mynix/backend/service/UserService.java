package com.mynix.backend.service;

import com.mynix.backend.dto.user.UserRequest;
import com.mynix.backend.dto.user.UserResponse;
import com.mynix.backend.model.User;

import java.util.List;

public interface UserService {

    User createAdmin();

    List<UserResponse> getAllUsers();

    UserResponse createUser(UserRequest request);

    UserResponse updateUser(Long id, UserRequest request);

    void deactivateUser(Long id);
}