package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.TaskHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class TaskModerationService {
    private final TaskMapper taskMapper;
    private final TaskHistoryService taskHistoryService;
    private final CreditService creditService;
    private final NotificationService notificationService;

    public TaskModerationService(
            TaskMapper taskMapper,
            TaskHistoryService taskHistoryService,
            CreditService creditService,
            NotificationService notificationService) {
        this.taskMapper = taskMapper;
        this.taskHistoryService = taskHistoryService;
        this.creditService = creditService;
        this.notificationService = notificationService;
    }

    public Task approveTask(Long adminUserId, Long taskId) {
        Task task = transition(taskId, "PENDING_REVIEW", "OPEN");
        taskHistoryService.recordHistory(taskId, "PENDING_REVIEW", "OPEN", adminUserId, "ADMIN_APPROVED");
        return task;
    }

    public Task rejectTask(Long adminUserId, Long taskId, String reason) {
        if (reason != null && reason.length() > 300) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "reason is too long");
        }
        Task task = transition(taskId, "PENDING_REVIEW", "REJECTED");
        String note = reason == null || reason.isBlank() ? "ADMIN_REJECTED" : "ADMIN_REJECTED; REASON=" + reason;
        taskHistoryService.recordHistory(taskId, "PENDING_REVIEW", "REJECTED", adminUserId, note);
        return task;
    }

    public void dismissAppeal(Long adminUserId, Long taskId) {
        Task task = transition(taskId, "DISPUTED", "COMPLETED");
        creditService.awardTaskReward(task);
        taskHistoryService.recordHistory(taskId, "DISPUTED", "COMPLETED", adminUserId, "ADMIN_DISMISSED_APPEAL");
        notificationService.notifyTaskStatusChanged(
                task, "DISPUTED", "COMPLETED", adminUserId, "管理员已驳回申诉，任务正常完成。");
    }

    public void punishAppeal(Long adminUserId, Long taskId) {
        Task task = transition(taskId, "DISPUTED", "COMPLETED");
        if (task.getProviderId() != null) {
            creditService.adjustCredit(task.getProviderId(), -20, "任务 #" + taskId + " 申诉处罚扣分", "TASK", taskId);
        }
        taskHistoryService.recordHistory(
                taskId, "DISPUTED", "COMPLETED", adminUserId, "ADMIN_PUNISHED_APPEAL; PROVIDER_DEDUCTED_20");
        notificationService.notifyTaskStatusChanged(
                task, "DISPUTED", "COMPLETED", adminUserId, "管理员已支持申诉并对执行者扣除20积分。");
    }

    private Task transition(Long taskId, String fromStatus, String toStatus) {
        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!fromStatus.equals(task.getStatus())
                || taskMapper.updateStatusById(taskId, fromStatus, toStatus, now) != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }
        task.setStatus(toStatus);
        task.setUpdatedAt(now);
        return task;
    }
}
