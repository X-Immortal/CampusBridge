package com.campusbridge.controller;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.CampusVerification;
import com.campusbridge.pojo.Result;
import com.campusbridge.service.VerificationService;
import com.campusbridge.utils.AuthContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/verifications")
public class VerificationController {

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private AuthContextUtil authContextUtil;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Result submitVerification(@RequestBody RequestDtos.CreateVerificationRequest request) {
        Long userId = authContextUtil.getCurrentUserId();
        Object result = verificationService.submit(userId, request);
        if (result == null) {
            return Result.error("VERIFY_001_INVALID_INPUT", "Invalid verification request");
        }
        if (result instanceof Long code) {
            if (code == -1L) {
                return Result.error(
                        "VERIFY_002_ALREADY_PENDING_OR_VERIFIED",
                        "Verification already pending or approved");
            }
            return Result.error("VERIFY_003_SUBMIT_FAILED", "Failed to submit verification");
        }
        return Result.success(result);
    }

    @GetMapping
    public Result getMyVerification() {
        Long userId = authContextUtil.getCurrentUserId();
        CampusVerification verification = verificationService.getMyLatest(userId);
        if (verification == null) {
            return Result.error("VERIFY_004_NOT_FOUND", "Verification record not found");
        }
        return Result.success(verification);
    }
}
