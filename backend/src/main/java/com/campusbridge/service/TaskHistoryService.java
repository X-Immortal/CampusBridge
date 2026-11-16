package com.campusbridge.service;

import com.campusbridge.enums.TaskStatus;
import com.campusbridge.enums.TaskHistoryEventType;

import java.time.LocalDateTime;

public interface TaskHistoryService {
    boolean hasReminder(Long taskId, TaskHistoryEventType eventType, LocalDateTime activeSince, LocalDateTime createdBefore);

    void recordHistory(
            Long taskId,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            Long operatorId,
            TaskHistoryEventType eventType,
            String note
    );

    void deleteByTaskId(Long taskId);
}
