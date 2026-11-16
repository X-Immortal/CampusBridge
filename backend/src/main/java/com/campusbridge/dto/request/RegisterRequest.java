package com.campusbridge.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    @NotNull(message = "username is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "username is required")
    public String username;

    @NotNull(message = "password is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "password is required")
    public String password;

    @NotNull(message = "phone is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "phone is required")
    public String phone;

    @NotNull(message = "email is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "email is required")
    public String email;

    @NotNull(message = "code is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "code is required")
    public String code;
}
