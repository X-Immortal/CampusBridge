package com.campusbridge.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Message {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String type;
    private String content;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
