package com.campusbridge.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {
    public String relatedType;
    public Long relatedId;
    public Long peerUserId;
    public String initialMessage;
}
