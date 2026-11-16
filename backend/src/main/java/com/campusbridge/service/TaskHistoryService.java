package com.campusbridge.service;

import java.time.LocalDateTime;

public interface TaskHistoryService {
    boolean hasReminder(Long taskId, String note, LocalDateTime activeSince, LocalDateTime createdBefore);

    void recordHistory(
            Long taskId,
            String fromStatus,
            String toStatus,
            Long operatorId,
            String note
    );

    void deleteByTaskId(Long taskId);
}
