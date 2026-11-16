package com.campusbridge.service.impl;

import com.campusbridge.entity.Post;
import com.campusbridge.entity.Reply;
import com.campusbridge.enums.NotificationType;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.ReplyNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReplyNotificationServiceImpl implements ReplyNotificationService {
    private final NotificationService notifications;

    @Override
    public void notifyReplyCreated(Post post, Reply reply, Reply parentReply) {
        Long receiverId = parentReply == null ? post.getAuthorId() : parentReply.getAuthorId();
        if (reply.getAuthorId().equals(receiverId)) return;

        NotificationType type = NotificationType.POST_REPLY;
        String title = parentReply == null ? type.getDefaultTitle() : "你的回复收到新回复";
        String content = "帖子“" + post.getTitle() + "”：" + reply.getContent();
        notifications.create(receiverId, type, title, content, post.getId());
    }
}
