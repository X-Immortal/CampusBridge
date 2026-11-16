package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.ConfirmEmailVerificationRequest;
import com.campusbridge.dto.request.CreateVerificationRequest;
import com.campusbridge.dto.view.VerificationCancelledView;
import com.campusbridge.dto.view.VerificationSubmissionView;
import com.campusbridge.entity.CampusVerification;
import com.campusbridge.service.VerificationService;
import com.campusbridge.utils.AuthContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/verifications")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;
    private final AuthContextUtil authContextUtil;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<VerificationSubmissionView> submitVerification(@RequestBody CreateVerificationRequest request) {
        Long userId = authContextUtil.getCurrentUserId();
        return ApiResponse.success(verificationService.submit(userId, request));
    }

    @PostMapping("/email/code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<VerificationSubmissionView> resendEmailVerificationCode() {
        Long userId = authContextUtil.getCurrentUserId();
        return ApiResponse.success(verificationService.resendEmailCode(userId));
    }

    @PostMapping("/email/confirm")
    public ApiResponse<VerificationSubmissionView> confirmEmailVerification(
            @RequestBody ConfirmEmailVerificationRequest request) {
        Long userId = authContextUtil.getCurrentUserId();
        return ApiResponse.success(verificationService.confirmEmail(userId, request));
    }

    @DeleteMapping("/current")
    public ApiResponse<VerificationCancelledView> cancelCurrentVerification() {
        Long userId = authContextUtil.getCurrentUserId();
        return ApiResponse.success(verificationService.cancelLatestPending(userId));
    }

    @GetMapping
    public ApiResponse<CampusVerification> getMyVerification() {
        Long userId = authContextUtil.getCurrentUserId();
        CampusVerification verification = verificationService.getMyLatest(userId);
        if (verification == null) {
            throw new BusinessException(ErrorCode.VERIFY_004_NOT_FOUND);
        }
        return ApiResponse.success(verification);
    }
}
