package com.campusbridge.dto.view;

import com.campusbridge.enums.ContentItemType;
import com.campusbridge.enums.ConversationType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class ConversationView {
    private Long id;
    private ConversationType type;
    private String title;
    private Long ownerId;
    private Long peerUserId;
    private ContentItemType relatedType;
    private Long relatedId;
    private Integer replyable;
    private LocalDateTime lastMessageAt;
    private String lastMessagePreview;
    private Integer unreadCount;
    private LocalDateTime lastReadAt;
    private LocalDateTime createdAt;

}
