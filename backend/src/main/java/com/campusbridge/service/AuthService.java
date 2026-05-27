package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;

public interface AuthService {
    Long register(RequestDtos.RegisterRequest request);

    Object login(RequestDtos.LoginRequest request);
}
