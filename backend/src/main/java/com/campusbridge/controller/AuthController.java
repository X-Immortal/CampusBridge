package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.dto.request.EmailRequest;
import com.campusbridge.dto.request.LoginRequest;
import com.campusbridge.dto.request.RegisterRequest;
import com.campusbridge.dto.view.EmailCodeSentView;
import com.campusbridge.dto.view.LoginView;
import com.campusbridge.service.AuthService;
import com.campusbridge.service.impl.EmailVerificationCodeSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private EmailVerificationCodeSender emailCodeSender;

    @PostMapping("/register/send-code")
    public ApiResponse<EmailCodeSentView> sendRegistrationCode(@RequestBody EmailRequest body) {
        String code = authService.sendRegistrationCode(body.email());
        return ApiResponse.success(new EmailCodeSentView(emailCodeSender.isDeliveryRequired(),
                emailCodeSender.isDebugCodeEnabled() ? code : null));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> register(@RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginView> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
