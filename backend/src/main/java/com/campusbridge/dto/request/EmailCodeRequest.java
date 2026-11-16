package com.campusbridge.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EmailCodeRequest(
        @NotNull(message = "验证码不能为空")
        @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "验证码不能为空")
        String code) {
}
