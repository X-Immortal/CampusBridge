package com.campusbridge.dto.request;

import com.campusbridge.enums.ContentItemType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {
    @NotNull(message = "relatedType is required")
    public ContentItemType relatedType;

    @NotNull(message = "relatedId is required")
    public Long relatedId;

    @NotNull(message = "peerUserId is required")
    public Long peerUserId;

    @Size(max = 1000, message = "initialMessage is too long")
    public String initialMessage;
}
