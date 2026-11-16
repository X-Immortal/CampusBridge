package com.campusbridge.service;

import com.campusbridge.dto.request.RegisterRequest;

public interface AuthService {
    Long register(RegisterRequest request);

    String sendRegistrationCode(String email);

    void changePassword(Long userId, String oldPassword, String newPassword);

    String sendAccountEmailCode(Long userId, String email);

    void confirmAccountEmailChange(Long userId, String code);

    void updatePhone(Long userId, String phone);
}
