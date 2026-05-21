package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.mapper.TaskHistoryMapper;
import com.campusbridge.pojo.TaskHistory;
import com.campusbridge.service.TaskHistoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TaskHistoryServiceImpl implements TaskHistoryService {
    private final TaskHistoryMapper taskHistoryMapper;

    public TaskHistoryServiceImpl(TaskHistoryMapper taskHistoryMapper) {
        this.taskHistoryMapper = taskHistoryMapper;
    }

    @Override
    public void recordHistory(
            Long taskId,
            String fromStatus,
            String toStatus,
            Long operatorId,
            String note) {
        if (taskId == null || toStatus == null || toStatus.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "任务历史记录参数不完整");
        }

        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setOperatorId(operatorId);
        history.setNote(note);
        history.setCreatedAt(LocalDateTime.now());

        int affected = taskHistoryMapper.insert(history);
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "任务历史记录写入失败");
        }
    }
}
