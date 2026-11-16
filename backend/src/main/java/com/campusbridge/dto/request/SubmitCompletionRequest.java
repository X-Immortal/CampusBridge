package com.campusbridge.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitCompletionRequest {
    @NotNull(message = "description is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "description is required")
    @Size(max = 1000, message = "description is too long")
    public String description;

    @Size(max = 9, message = "attachmentIds exceeds limit")
    public List<Long> attachmentIds;
}
