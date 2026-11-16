package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.entity.TaskHistory;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.enums.TaskHistoryEventType;
import com.campusbridge.mapper.TaskHistoryMapper;
import com.campusbridge.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TaskHistoryServiceImpl implements TaskHistoryService {
    private final TaskHistoryMapper taskHistoryMapper;

    @Override
    public boolean hasReminder(
            Long taskId, TaskHistoryEventType eventType, LocalDateTime activeSince, LocalDateTime createdBefore) {
        return taskHistoryMapper.existsReminder(taskId, eventType, activeSince, createdBefore);
    }

    @Override
    public void recordHistory(
            Long taskId,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            Long operatorId,
            TaskHistoryEventType eventType,
            String note) {
        if (taskId == null || toStatus == null || eventType == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "任务历史记录参数不完整");
        }

        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setOperatorId(operatorId);
        history.setEventType(eventType);
        history.setNote(note);
        history.setCreatedAt(LocalDateTime.now());

        int affected = taskHistoryMapper.insert(history);
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "任务历史记录写入失败");
        }
    }

    @Override
    public void deleteByTaskId(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId must not be null");
        }
        taskHistoryMapper.deleteByTaskId(taskId);
    }
}
