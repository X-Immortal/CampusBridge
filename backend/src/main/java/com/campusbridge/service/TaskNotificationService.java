package com.campusbridge.service;

import com.campusbridge.entity.Review;
import com.campusbridge.entity.Task;
import com.campusbridge.entity.TaskApplication;
import com.campusbridge.enums.TaskStatus;

public interface TaskNotificationService {
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
            TaskStatus toStatus,
            Long operatorId,
            String reason);

    void notifyOpenTaskTimeoutReminder(Task task);

    void notifyTaskConfirmationReminder(Task task);
}
