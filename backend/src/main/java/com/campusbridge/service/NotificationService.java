package com.campusbridge.service;

import com.campusbridge.pojo.Post;
import com.campusbridge.pojo.Reply;
import com.campusbridge.pojo.Task;
import com.campusbridge.pojo.TaskApplication;

public interface NotificationService {
    void notifyPostReplyCreated(
            Post post,
            Reply reply,
            Reply parentReply,
            Long operatorId);

    void notifyTaskApplicationCreated(
            Task task,
            TaskApplication application,
            Long operatorId);

    void notifyTaskApplicationAccepted(
            Task task,
            TaskApplication application,
            Long operatorId);

    void notifyTaskStatusChanged(
            Task task,
            String fromStatus,
            String toStatus,
            Long operatorId,
            String reason);

    void notifyTaskTimeoutReminder(
            Task task,
            Long recipientId,
            Long operatorId,
            String reminderType);
}
