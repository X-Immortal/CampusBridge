package com.campusbridge.controller;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.pojo.Result;
import com.campusbridge.service.AuthService;
import com.campusbridge.service.impl.EmailVerificationCodeSender;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
    public Result changePassword(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {
        Long userId = currentUserResolver.requireCurrentUserId(request);
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "旧密码和新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "新密码至少6位");
        }
        authService.changePassword(userId, oldPassword, newPassword);
        return Result.success();
    }

    @PutMapping("/email")
    public Result sendEmailChangeCode(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {
        Long userId = currentUserResolver.requireCurrentUserId(request);
        String newEmail = body.get("email");
        if (newEmail == null || newEmail.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "新邮箱不能为空");
        }
        String code = authService.sendAccountEmailCode(userId, newEmail);
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("email", newEmail);
        payload.put("sent", emailCodeSender.isDeliveryRequired());
        if (emailCodeSender.isDebugCodeEnabled()) {
            payload.put("debugCode", code);
        }
        return Result.success(payload);
    }

    @PutMapping("/email/confirm")
    public Result confirmEmailChange(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {
        Long userId = currentUserResolver.requireCurrentUserId(request);
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "验证码不能为空");
        }
        authService.confirmAccountEmailChange(userId, code);
        return Result.success();
    }

    @PutMapping("/phone")
    public Result updatePhone(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {
        Long userId = currentUserResolver.requireCurrentUserId(request);
        String phone = body.get("phone");
        if (phone != null && !phone.isBlank() && !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "手机号格式不正确");
        }
        authService.updatePhone(userId, phone);
        return Result.success();
    }
}
