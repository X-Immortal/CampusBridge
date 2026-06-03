package com.campusbridge.service.impl;

import com.campusbridge.common.ErrorCode;
import com.campusbridge.common.BusinessException;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.pojo.Task;
import com.campusbridge.pojo.User;
import com.campusbridge.service.TaskHistoryService;
import com.campusbridge.service.TaskModerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class TaskModerationServiceImpl implements TaskModerationService {

    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String HISTORY_NOTE_APPROVED = "TASK_APPROVED";
    private static final String HISTORY_NOTE_REJECTED = "TASK_REJECTED";
    private static final int MAX_REVIEW_NOTE_LENGTH = 300;
    private static final int MAX_REJECT_REASON_LENGTH = 300;
    private static final String ROLE_ADMIN = "ADMIN";

    private final TaskMapper taskMapper;
    private final TaskHistoryService taskHistoryService;
    private final UserMapper userMapper;

    public TaskModerationServiceImpl(
            TaskMapper taskMapper,
            TaskHistoryService taskHistoryService,
            UserMapper userMapper) {
        this.taskMapper = taskMapper;
        this.taskHistoryService = taskHistoryService;
        this.userMapper = userMapper;
    }

    @Override
    public List<Task> listPendingReviewTasks(Integer page, Integer size) {
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 20;
        }
        int offset = (page - 1) * size;
        return taskMapper.selectTasksByStatus(STATUS_PENDING_REVIEW, offset, size);
    }

    @Override
    public int countPendingReviewTasks() {
        return taskMapper.countTasksByStatus(STATUS_PENDING_REVIEW);
    }

    @Override
    public Task approveTask(Long taskId, Long adminId, String reviewNote) {
        requireTaskId(taskId);
        requireAdminId(adminId);
        validateReviewNote(reviewNote);

        Task task = requireTask(taskId);
        validateTaskStatusForModeration(task);

        String oldStatus = task.getStatus();
        task.setStatus(STATUS_OPEN);
        task.setUpdatedAt(LocalDateTime.now());

        int affectedRows = taskMapper.updateStatusById(
                taskId, STATUS_PENDING_REVIEW, STATUS_OPEN, LocalDateTime.now());
        if (affectedRows != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to approve task");
        }

        String note = reviewNote != null && !reviewNote.isBlank()
                ? HISTORY_NOTE_APPROVED + ": " + reviewNote
                : HISTORY_NOTE_APPROVED;
        taskHistoryService.recordHistory(taskId, oldStatus, STATUS_OPEN, adminId, note);

        return task;
    }

    @Override
    public Task rejectTask(Long taskId, Long adminId, String rejectReason) {
        requireTaskId(taskId);
        requireAdminId(adminId);
        requireRejectReason(rejectReason);

        Task task = requireTask(taskId);
        validateTaskStatusForModeration(task);

        String oldStatus = task.getStatus();
        task.setStatus(STATUS_REJECTED);
        task.setUpdatedAt(LocalDateTime.now());

        int affectedRows = taskMapper.updateStatusById(
                taskId, STATUS_PENDING_REVIEW, STATUS_REJECTED, LocalDateTime.now());
        if (affectedRows != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to reject task");
        }

        String note = HISTORY_NOTE_REJECTED + ": " + rejectReason;
        taskHistoryService.recordHistory(taskId, oldStatus, STATUS_REJECTED, adminId, note);

        return task;
    }

    private void requireTaskId(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId is required");
        }
    }

    private void requireAdminId(Long adminId) {
        if (adminId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "adminId is required");
        }
        User user = userMapper.selectById(adminId);
        if (user == null || !ROLE_ADMIN.equals(user.getRole())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED,
                    "Only administrators can perform this action");
        }
    }

    private void validateReviewNote(String reviewNote) {
        if (reviewNote != null && reviewNote.length() > MAX_REVIEW_NOTE_LENGTH) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "reviewNote is too long (max " + MAX_REVIEW_NOTE_LENGTH + " characters)");
        }
    }

    private void requireRejectReason(String rejectReason) {
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "rejectReason is required");
        }
        if (rejectReason.length() > MAX_REJECT_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "rejectReason is too long (max " + MAX_REJECT_REASON_LENGTH + " characters)");
        }
    }

    private Task requireTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }
        return task;
    }

    private void validateTaskStatusForModeration(Task task) {
        if (!STATUS_PENDING_REVIEW.equals(task.getStatus())) {
            throw new BusinessException(
                    ErrorCode.TASK_002_INVALID_STATUS_TRANSITION,
                    "Task is not in PENDING_REVIEW status");
        }
    }
}
