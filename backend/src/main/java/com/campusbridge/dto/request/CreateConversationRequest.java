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
public class CreateConversationRequest {
    @NotNull(message = "relatedType is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "relatedType is required")
    public String relatedType;

    @NotNull(message = "relatedId is required")
    public Long relatedId;

    @NotNull(message = "peerUserId is required")
    public Long peerUserId;

    @Size(max = 1000, message = "initialMessage is too long")
    public String initialMessage;
}
