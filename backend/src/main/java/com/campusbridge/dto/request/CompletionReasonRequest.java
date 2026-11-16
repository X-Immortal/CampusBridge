package com.campusbridge.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompletionReasonRequest(
        @NotNull(message = "reason is required")
        @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "reason is required")
        @Size(max = 500, message = "reason is too long (max 500 characters)")
        String reason) {
}
