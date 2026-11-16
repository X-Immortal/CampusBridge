package com.campusbridge.dto.view;

import com.campusbridge.enums.VerificationStatus;

import java.time.LocalDateTime;

public record VerificationReviewView(Long id, VerificationStatus status, Long reviewerId, LocalDateTime reviewedAt) {
}
