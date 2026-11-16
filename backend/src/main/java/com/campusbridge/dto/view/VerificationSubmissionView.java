package com.campusbridge.dto.view;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

public record VerificationSubmissionView(Long id, String status, Long userId, String method,
        LocalDateTime createdAt, LocalDateTime reviewedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String campusEmail,
        @JsonInclude(JsonInclude.Include.NON_NULL) String emailDelivery,
        @JsonInclude(JsonInclude.Include.NON_NULL) String debugCode) {
}
