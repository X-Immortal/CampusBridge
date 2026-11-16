package com.campusbridge.service;

import com.campusbridge.entity.Post;
import com.campusbridge.entity.Reply;
import com.campusbridge.entity.Review;
import com.campusbridge.entity.Task;
import com.campusbridge.entity.TaskApplication;

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

    void notifyTaskApplicationRejected(
            Task task,
            TaskApplication application,
            Long operatorId);

    void notifyTaskApplicationWithdrawn(
            Task task,
            TaskApplication application,
            Long operatorId);

    void notifyTaskReviewCreated(
            Task task,
            Review review,
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
