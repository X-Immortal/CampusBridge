package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.ChangePasswordRequest;
import com.campusbridge.dto.request.EmailCodeRequest;
import com.campusbridge.dto.request.EmailRequest;
import com.campusbridge.dto.request.UpdatePhoneRequest;
import com.campusbridge.dto.view.EmailChangeCodeSentView;
import com.campusbridge.service.AuthService;
import com.campusbridge.service.impl.EmailVerificationCodeSender;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final CurrentUserResolver currentUserResolver;
    private final AuthService authService;
    private final EmailVerificationCodeSender emailCodeSender;

    public AccountController(
            CurrentUserResolver currentUserResolver,
            AuthService authService,
            EmailVerificationCodeSender emailCodeSender) {
        this.currentUserResolver = currentUserResolver;
        this.authService = authService;
        this.emailCodeSender = emailCodeSender;
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            HttpServletRequest request,
            @RequestBody ChangePasswordRequest body) {
        Long userId = currentUserResolver.requireCurrentUserId(request);
        String oldPassword = body.oldPassword();
        String newPassword = body.newPassword();
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "旧密码和新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "新密码至少6位");
        }
        authService.changePassword(userId, oldPassword, newPassword);
        return ApiResponse.success();
    }

    @PutMapping("/email")
    public ApiResponse<EmailChangeCodeSentView> sendEmailChangeCode(
            HttpServletRequest request,
            @RequestBody EmailRequest body) {
        Long userId = currentUserResolver.requireCurrentUserId(request);
        String newEmail = body.email();
        if (newEmail == null || newEmail.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "新邮箱不能为空");
        }
        String code = authService.sendAccountEmailCode(userId, newEmail);
        return ApiResponse.success(new EmailChangeCodeSentView(newEmail,
                emailCodeSender.isDeliveryRequired(), emailCodeSender.isDebugCodeEnabled() ? code : null));
    }

    @PutMapping("/email/confirm")
    public ApiResponse<Void> confirmEmailChange(
            HttpServletRequest request,
            @RequestBody EmailCodeRequest body) {
        Long userId = currentUserResolver.requireCurrentUserId(request);
        String code = body.code();
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "验证码不能为空");
        }
        authService.confirmAccountEmailChange(userId, code);
        return ApiResponse.success();
    }

    @PutMapping("/phone")
    public ApiResponse<Void> updatePhone(
            HttpServletRequest request,
            @RequestBody UpdatePhoneRequest body) {
        Long userId = currentUserResolver.requireCurrentUserId(request);
        String phone = body.phone();
        if (phone != null && !phone.isBlank() && !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "手机号格式不正确");
        }
        authService.updatePhone(userId, phone);
        return ApiResponse.success();
    }
}
