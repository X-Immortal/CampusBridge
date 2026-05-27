package com.campusbridge.service;

public interface TaskHistoryService {
    void recordHistory(
            Long taskId,
            String fromStatus,
            String toStatus,
            Long operatorId,
            String note
    );
}
