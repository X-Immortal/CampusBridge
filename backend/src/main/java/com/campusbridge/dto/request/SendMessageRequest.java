package com.campusbridge.dto.request;

import com.campusbridge.enums.MessageType;
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
public class SendMessageRequest {
    @NotNull(message = "type is required")
    public MessageType type;

    @NotNull(message = "content is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "content is required")
    @Size(max = 2000, message = "content is too long")
    public String content;

    @Size(max = 9, message = "attachmentIds exceeds limit")
    public List<Long> attachmentIds;
}
