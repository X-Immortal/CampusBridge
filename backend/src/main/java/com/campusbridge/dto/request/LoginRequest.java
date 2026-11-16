package com.campusbridge.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotNull(message = "username is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "username is required")
    public String username;

    @NotNull(message = "password is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "password is required")
    public String password;
}
