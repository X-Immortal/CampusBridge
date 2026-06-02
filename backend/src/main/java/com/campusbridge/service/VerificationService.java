package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.CampusVerification;

import java.util.List;

public interface VerificationService {
    Object submit(Long userId, RequestDtos.CreateVerificationRequest request);

    Object confirmEmail(Long userId, RequestDtos.ConfirmEmailVerificationRequest request);

    Object resendEmailCode(Long userId);

    CampusVerification getMyLatest(Long userId);

    List<CampusVerification> listPending(int page, int size);

    Object review(Long adminUserId, Long verificationId, boolean approved, String rejectReason);
}
