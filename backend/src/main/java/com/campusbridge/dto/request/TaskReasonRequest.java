package com.campusbridge.dto.request;

import jakarta.validation.constraints.Size;

public record TaskReasonRequest(
        @Size(max = 300, message = "reason is too long")
        String reason) {
}
