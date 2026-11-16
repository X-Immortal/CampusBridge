package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.dto.request.EmailRequest;
import com.campusbridge.dto.request.LoginRequest;
import com.campusbridge.dto.request.RegisterRequest;
import com.campusbridge.dto.view.EmailCodeSentView;
import com.campusbridge.dto.view.LoginView;
import com.campusbridge.security.UserPrincipal;
import com.campusbridge.security.JwtTokenIssuer;
import com.campusbridge.service.AuthService;
import com.campusbridge.service.impl.EmailVerificationCodeSender;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationCodeSender emailCodeSender;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenIssuer tokens;

    @PostMapping("/register/send-code")
    public ApiResponse<EmailCodeSentView> sendRegistrationCode(@Valid @RequestBody EmailRequest body) {
        String code = authService.sendRegistrationCode(body.email());
        return ApiResponse.success(new EmailCodeSentView(emailCodeSender.isDeliveryRequired(),
                emailCodeSender.isDebugCodeEnabled() ? code : null));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginView> login(@Valid @RequestBody LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username, request.password));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ApiResponse.success(new LoginView(
                principal.getUserId(), principal.getUsername(), principal.getRole(),
                tokens.issueAccessToken(principal.getUserId(), principal.getRole()),
                tokens.issueRefreshToken(principal.getUserId())));
    }
}
