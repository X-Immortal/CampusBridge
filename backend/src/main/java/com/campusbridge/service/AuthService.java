package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;

public interface AuthService {
    Long register(RequestDtos.RegisterRequest request);

    String sendRegistrationCode(String email);

    Object login(RequestDtos.LoginRequest request);

    void changePassword(Long userId, String oldPassword, String newPassword);

    String sendAccountEmailCode(Long userId, String email);

    void confirmAccountEmailChange(Long userId, String code);

    void updatePhone(Long userId, String phone);
}
