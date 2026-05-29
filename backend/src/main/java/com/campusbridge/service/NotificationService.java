package com.campusbridge.service;

import com.campusbridge.pojo.Task;
import com.campusbridge.pojo.TaskApplication;

public interface NotificationService {
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
}
