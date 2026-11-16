package com.campusbridge.dto.view;

import com.campusbridge.entity.Notification;
import com.campusbridge.enums.ContentItemType;
import com.campusbridge.enums.NotificationType;
import java.time.LocalDateTime;

public record NotificationView(Long id, NotificationType type, String title, String content,
                               ContentItemType relatedType, Long relatedId,
                               LocalDateTime readAt, LocalDateTime createdAt) {
    public static NotificationView from(Notification notification) {
        return new NotificationView(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getContent(), notification.getRelatedType(), notification.getRelatedId(),
                notification.getReadAt(), notification.getCreatedAt());
    }
}
