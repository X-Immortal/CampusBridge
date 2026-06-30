package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.CampusVerification;

import com.campusbridge.dto.response.ResponseDtos;

public interface VerificationService {
    Object submit(Long userId, RequestDtos.CreateVerificationRequest request);

    Object confirmEmail(Long userId, RequestDtos.ConfirmEmailVerificationRequest request);

    Object resendEmailCode(Long userId);

    Object cancelLatestPending(Long userId);

    CampusVerification getMyLatest(Long userId);

    ResponseDtos.PagedResult<CampusVerification> listPending(int page, int size);

    Object review(Long adminUserId, Long verificationId, boolean approved, String rejectReason);
}
