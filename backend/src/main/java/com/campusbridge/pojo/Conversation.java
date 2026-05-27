package com.campusbridge.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {
    private Long id;
    private String type;
    private String title;
    private Long ownerId;
    private Long peerUserId;
    private String relatedType;
    private Long relatedId;
    private Integer replyable;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
