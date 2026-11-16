package com.campusbridge.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotNull(message = "旧密码不能为空")
        @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "旧密码不能为空")
        String oldPassword,
        @NotNull(message = "新密码不能为空")
        @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "新密码不能为空")
        @Size(min = 6, message = "新密码至少6位")
        String newPassword) {
}
