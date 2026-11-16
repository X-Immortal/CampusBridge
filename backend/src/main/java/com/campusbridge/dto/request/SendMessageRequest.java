package com.campusbridge.dto.request;

import com.campusbridge.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    public MessageType type;
    public String content;
    public List<Long> attachmentIds;
}
