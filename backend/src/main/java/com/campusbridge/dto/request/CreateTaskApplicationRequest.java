package com.campusbridge.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskApplicationRequest {
    @NotNull(message = "message is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "message is required")
    @Size(max = 300, message = "message is too long")
    public String message;
}
