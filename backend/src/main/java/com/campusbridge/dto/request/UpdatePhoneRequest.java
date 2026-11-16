package com.campusbridge.dto.request;

import jakarta.validation.constraints.Pattern;

public record UpdatePhoneRequest(
        @Pattern(regexp = "(?:\\p{javaWhitespace}*|1[3-9]\\d{9})", message = "手机号格式不正确")
        String phone) {
}
