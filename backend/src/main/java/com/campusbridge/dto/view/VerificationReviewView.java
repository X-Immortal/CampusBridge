package com.campusbridge.dto.view;

import java.time.LocalDateTime;

public record VerificationReviewView(Long id, String status, Long reviewerId, LocalDateTime reviewedAt) {
}
