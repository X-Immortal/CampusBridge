package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.dto.request.ChangePasswordRequest;
import com.campusbridge.dto.request.EmailCodeRequest;
import com.campusbridge.dto.request.EmailRequest;
import com.campusbridge.dto.request.UpdatePhoneRequest;
import com.campusbridge.dto.view.EmailChangeCodeSentView;
import com.campusbridge.service.AuthService;
import com.campusbridge.service.impl.EmailVerificationCodeSender;
import com.campusbridge.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

    private final AuthService authService;
    private final EmailVerificationCodeSender emailCodeSender;

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest body) {
        Long userId = principal.getUserId();
        String oldPassword = body.oldPassword();
        String newPassword = body.newPassword();
        authService.changePassword(userId, oldPassword, newPassword);
        return ApiResponse.success();
    }

    @PutMapping("/email")
    public ApiResponse<EmailChangeCodeSentView> sendEmailChangeCode(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody EmailRequest body) {
        Long userId = principal.getUserId();
        String newEmail = body.email();
        String code = authService.sendAccountEmailCode(userId, newEmail);
        return ApiResponse.success(new EmailChangeCodeSentView(newEmail,
                emailCodeSender.isDeliveryRequired(), emailCodeSender.isDebugCodeEnabled() ? code : null));
    }

    @PutMapping("/email/confirm")
    public ApiResponse<Void> confirmEmailChange(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody EmailCodeRequest body) {
        Long userId = principal.getUserId();
        String code = body.code();
        authService.confirmAccountEmailChange(userId, code);
        return ApiResponse.success();
    }

    @PutMapping("/phone")
    public ApiResponse<Void> updatePhone(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdatePhoneRequest body) {
        Long userId = principal.getUserId();
        String phone = body.phone();
        authService.updatePhone(userId, phone);
        return ApiResponse.success();
    }
}
