package com.campusbridge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.query.NotificationQuery;
import com.campusbridge.dto.view.NotificationView;
import com.campusbridge.dto.view.ReadAllNotificationsView;
import com.campusbridge.entity.Notification;
import com.campusbridge.enums.NotificationType;
import com.campusbridge.mapper.NotificationMapper;
import com.campusbridge.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {
    private final NotificationMapper notificationMapper;

    @Override
    public void create(Long receiverId, NotificationType type, String title, String content,
                       Long relatedId) {
        Notification notification = new Notification();
        notification.setReceiverId(receiverId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedType(type.getRelatedType());
        notification.setRelatedId(relatedId);
        notification.setCreatedAt(LocalDateTime.now());
        if (notificationMapper.insert(notification) != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "Failed to create notification");
        }
    }

    @Override
    public PagedResult<NotificationView> list(Long userId, NotificationQuery query) {
        var filters = Wrappers.<Notification>lambdaQuery().eq(Notification::getReceiverId, userId)
                .isNull(query.isUnreadOnly(), Notification::getReadAt)
                .in(query.getTypes() != null && !query.getTypes().isEmpty(), Notification::getType, query.getTypes());
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().trim();
            filters.and(group -> group.like(Notification::getTitle, keyword).or().like(Notification::getContent, keyword));
        }
        Page<Notification> result = notificationMapper.selectPage(new Page<>(query.getPage(), query.getSize()),
                filters.orderByDesc(Notification::getCreatedAt, Notification::getId));
        return new PagedResult<>(result.getRecords().stream().map(NotificationView::from).toList(),
                query.getPage(), query.getSize(), result.getTotal());
    }

    @Override
    public long countUnread(Long userId) {
        return notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getReceiverId, userId).isNull(Notification::getReadAt));
    }

    @Override
    public NotificationView markRead(Long userId, Long notificationId) {
        notificationMapper.update(null, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getId, notificationId).eq(Notification::getReceiverId, userId)
                .isNull(Notification::getReadAt).set(Notification::getReadAt, LocalDateTime.now()));
        Notification notification = notificationMapper.selectOne(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getId, notificationId).eq(Notification::getReceiverId, userId));
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOTICE_001_NOT_FOUND);
        }
        return NotificationView.from(notification);
    }

    @Override
    public ReadAllNotificationsView markAllRead(Long userId) {
        int updated = notificationMapper.update(null, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getReceiverId, userId).isNull(Notification::getReadAt)
                .set(Notification::getReadAt, LocalDateTime.now()));
        return new ReadAllNotificationsView(updated);
    }
}
