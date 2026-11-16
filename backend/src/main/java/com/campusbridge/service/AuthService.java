package com.campusbridge.service;

import com.campusbridge.dto.request.LoginRequest;
import com.campusbridge.dto.request.RegisterRequest;
import com.campusbridge.dto.view.LoginView;

public interface AuthService {
    Long register(RegisterRequest request);

    String sendRegistrationCode(String email);

    LoginView login(LoginRequest request);

    void changePassword(Long userId, String oldPassword, String newPassword);

    String sendAccountEmailCode(Long userId, String email);

    void confirmAccountEmailChange(Long userId, String code);

    void updatePhone(Long userId, String phone);
}
