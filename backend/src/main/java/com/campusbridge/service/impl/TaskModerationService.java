package com.campusbridge.service.impl;

import com.campusbridge.enums.CreditSourceType;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.entity.Task;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.enums.TaskHistoryEventType;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.TaskNotificationService;
import com.campusbridge.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
public class TaskModerationService {
    private final TaskMapper taskMapper;
    private final TaskHistoryService taskHistoryService;
    private final CreditService creditService;
    private final TaskNotificationService notificationService;

    public Task approveTask(Long adminUserId, Long taskId) {
        Task task = transition(taskId, TaskStatus.PENDING_REVIEW, TaskStatus.OPEN);
        taskHistoryService.recordHistory(taskId, TaskStatus.PENDING_REVIEW, TaskStatus.OPEN,
                adminUserId, TaskHistoryEventType.MODERATION_APPROVED, null);
        return task;
    }

    public Task rejectTask(Long adminUserId, Long taskId, String reason) {
        Task task = transition(taskId, TaskStatus.PENDING_REVIEW, TaskStatus.REJECTED);
        taskHistoryService.recordHistory(taskId, TaskStatus.PENDING_REVIEW, TaskStatus.REJECTED,
                adminUserId, TaskHistoryEventType.MODERATION_REJECTED, reason);
        return task;
    }

    public void dismissAppeal(Long adminUserId, Long taskId) {
        Task task = transition(taskId, TaskStatus.DISPUTED, TaskStatus.COMPLETED);
        creditService.awardTaskReward(task);
        taskHistoryService.recordHistory(taskId, TaskStatus.DISPUTED, TaskStatus.COMPLETED,
                adminUserId, TaskHistoryEventType.APPEAL_DISMISSED, null);
        notificationService.notifyTaskStatusChanged(
                task, TaskStatus.COMPLETED, adminUserId, "管理员已驳回申诉，任务正常完成。");
    }

    public void punishAppeal(Long adminUserId, Long taskId) {
        Task task = transition(taskId, TaskStatus.DISPUTED, TaskStatus.COMPLETED);
        if (task.getProviderId() != null) {
            creditService.adjustCredit(task.getProviderId(), -20, "任务 #" + taskId + " 申诉处罚扣分", CreditSourceType.TASK, taskId);
        }
        taskHistoryService.recordHistory(
                taskId, TaskStatus.DISPUTED, TaskStatus.COMPLETED, adminUserId, TaskHistoryEventType.APPEAL_PUNISHED, "执行者扣除20积分");
        notificationService.notifyTaskStatusChanged(
                task, TaskStatus.COMPLETED, adminUserId, "管理员已支持申诉并对执行者扣除20积分。");
    }

    private Task transition(Long taskId, TaskStatus fromStatus, TaskStatus toStatus) {
        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        if (fromStatus != task.getStatus()
                || taskMapper.updateStatusById(taskId, fromStatus, toStatus, now) != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }
        task.setStatus(toStatus);
        task.setUpdatedAt(now);
        return task;
    }
}
