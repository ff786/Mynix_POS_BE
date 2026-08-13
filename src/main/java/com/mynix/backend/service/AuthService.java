package com.mynix.backend.service;

import com.mynix.backend.dto.auth.LoginRequest;
import com.mynix.backend.dto.auth.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

}