package com.campusbridge.dto.view;

import com.campusbridge.enums.EmailDeliveryStatus;
import com.campusbridge.enums.VerificationMethod;
import com.campusbridge.enums.VerificationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

public record VerificationSubmissionView(Long id, VerificationStatus status, Long userId, VerificationMethod method,
        LocalDateTime createdAt, LocalDateTime reviewedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String campusEmail,
        @JsonInclude(JsonInclude.Include.NON_NULL) EmailDeliveryStatus emailDelivery,
        @JsonInclude(JsonInclude.Include.NON_NULL) String debugCode) {
}
