package com.campusbridge.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateReplyRequest {
    public Long parentReplyId;

    @NotNull(message = "content is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "content is required")
    public String content;
}
