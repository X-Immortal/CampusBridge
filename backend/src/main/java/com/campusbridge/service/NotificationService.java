package com.campusbridge.service;

import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.query.NotificationQuery;
import com.campusbridge.dto.view.NotificationView;
import com.campusbridge.dto.view.ReadAllNotificationsView;
import com.campusbridge.enums.NotificationType;

public interface NotificationService {
    void create(Long receiverId, NotificationType type, String title, String content,
                Long relatedId);
    PagedResult<NotificationView> list(Long userId, NotificationQuery query);
    long countUnread(Long userId);
    NotificationView markRead(Long userId, Long notificationId);
    ReadAllNotificationsView markAllRead(Long userId);
}
