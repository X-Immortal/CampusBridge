package com.campusbridge.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EmailRequest(
        @NotNull(message = "邮箱不能为空")
        @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "邮箱不能为空")
        String email) {
}
