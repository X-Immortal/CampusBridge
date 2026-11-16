package com.campusbridge.service;

import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.request.ConfirmEmailVerificationRequest;
import com.campusbridge.dto.request.CreateVerificationRequest;
import com.campusbridge.dto.view.VerificationCancelledView;
import com.campusbridge.dto.view.VerificationReviewView;
import com.campusbridge.dto.view.VerificationSubmissionView;
import com.campusbridge.entity.CampusVerification;

public interface VerificationService {
    VerificationSubmissionView submit(Long userId, CreateVerificationRequest request);

    VerificationSubmissionView confirmEmail(Long userId, ConfirmEmailVerificationRequest request);

    VerificationSubmissionView resendEmailCode(Long userId);

    VerificationCancelledView cancelLatestPending(Long userId);

    CampusVerification getMyLatest(Long userId);

    PagedResult<CampusVerification> listPending(int page, int size);

    VerificationReviewView review(Long adminUserId, Long verificationId, boolean approved, String rejectReason);
}
