package com.campusbridge.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmEmailVerificationRequest {
    @NotNull(message = "code is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "code is required")
    public String code;
}
