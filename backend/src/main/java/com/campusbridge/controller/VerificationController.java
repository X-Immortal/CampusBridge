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
import com.campusbridge.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<VerificationSubmissionView> submitVerification(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateVerificationRequest request) {
        Long userId = principal.getUserId();
        return ApiResponse.success(verificationService.submit(userId, request));
    }

    @PostMapping("/email/code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<VerificationSubmissionView> resendEmailVerificationCode(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal.getUserId();
        return ApiResponse.success(verificationService.resendEmailCode(userId));
    }

    @PostMapping("/email/confirm")
    public ApiResponse<VerificationSubmissionView> confirmEmailVerification(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ConfirmEmailVerificationRequest request) {
        Long userId = principal.getUserId();
        return ApiResponse.success(verificationService.confirmEmail(userId, request));
    }

    @DeleteMapping("/current")
    public ApiResponse<VerificationCancelledView> cancelCurrentVerification(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal.getUserId();
        return ApiResponse.success(verificationService.cancelLatestPending(userId));
    }

    @GetMapping
    public ApiResponse<CampusVerification> getMyVerification(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal.getUserId();
        CampusVerification verification = verificationService.getMyLatest(userId);
        if (verification == null) {
            throw new BusinessException(ErrorCode.VERIFY_004_NOT_FOUND);
        }
        return ApiResponse.success(verification);
    }
}
