package com.campusbridge.dto.view;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class ConversationView {
    private Long id;
    private String type;
    private String title;
    private Long ownerId;
    private Long peerUserId;
    private String relatedType;
    private Long relatedId;
    private Integer replyable;
    private LocalDateTime lastMessageAt;
    private String lastMessagePreview;
    private Integer unreadCount;
    private LocalDateTime lastReadAt;
    private LocalDateTime createdAt;

}
