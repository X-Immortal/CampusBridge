package com.campusbridge.service.impl;

import com.campusbridge.enums.CreditSourceType;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.query.TaskQuery;
import com.campusbridge.dto.request.CreateTaskRequest;
import com.campusbridge.dto.request.SaveTaskDraftRequest;
import com.campusbridge.dto.request.TaskStatusTransitionRequest;
import com.campusbridge.dto.request.UpdateTaskRequest;
import com.campusbridge.dto.view.TaskPageView;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.entity.CampusVerification;
import com.campusbridge.entity.Task;
import com.campusbridge.entity.TaskApplication;
import com.campusbridge.enums.ApplicationStatus;
import com.campusbridge.enums.RewardType;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.enums.TaskHistoryEventType;
import com.campusbridge.enums.TaskStatusTransitionAction;
import com.campusbridge.enums.VerificationStatus;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.mapper.TaskApplicationMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.service.ContentModerationService;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.TaskNotificationService;
import com.campusbridge.service.TaskHistoryService;
import com.campusbridge.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {


    private final TaskMapper taskMapper;
    private final CampusVerificationMapper campusVerificationMapper;
    private final TaskHistoryService taskHistoryService;
    private final ContentModerationService contentModerationService;
    private final TaskNotificationService notificationService;
    private final TaskApplicationMapper taskApplicationMapper;
    private final CreditService creditService;

    @Override
    public TaskView createTask(Long requesterId, CreateTaskRequest request) {
        requireRequesterId(requesterId);
        requireVerifiedUser(requesterId);
        validateRewardAmount(request.getRewardType(), request.getRewardAmount());
        contentModerationService.assertTaskPublishAllowed(requesterId, request);

        Task task = buildTask(requesterId, request);
        int affectedRows = taskMapper.insert(task);
        if (affectedRows != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create task");
        }

        taskHistoryService.recordHistory(
                task.getId(),
                null,
                task.getStatus(),
                requesterId,
                TaskHistoryEventType.CREATED, null);
        return TaskView.from(task);
    }

    private void requireRequesterId(Long requesterId) {
        if (requesterId == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "requesterId must not be null");
        }
    }

    @Override
    public TaskPageView listTasks(TaskQuery query) {
        Integer page = query.getPage();
        Integer size = query.getSize();
        query.setPage(page != null && page > 0 ? page : 1);
        query.setSize(size != null && size > 0 && size <= 100 ? size : 20);
        query.setIncludePrivate(false);

        Page<TaskView> result = taskMapper.selectWithFilters(new Page<>(query.getPage(), query.getSize()), query);
        return new TaskPageView(result.getRecords(), result.getTotal(), query.getPage(), query.getSize());
    }

    @Override
    public TaskView getTaskDetail(Long taskId) {
        return getTaskDetail(taskId, null);
    }

    @Override
    public TaskView getTaskDetail(Long taskId, Long currentUserId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId must not be null");
        }
        TaskView task = taskMapper.selectDetailById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }
        if ((task.getStatus() == TaskStatus.DRAFT || task.getStatus() == TaskStatus.PENDING_REVIEW
                || task.getStatus() == TaskStatus.REJECTED)
                && !task.getRequesterId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }
        return task;
    }

    @Override
    public TaskView updateTask(Long operatorId, Long taskId, UpdateTaskRequest request) {
        requireOperatorId(operatorId);
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId must not be null");
        }

        Task existingTask = taskMapper.selectByIdForUpdate(taskId);
        if (existingTask == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        if (!operatorId.equals(existingTask.getRequesterId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        if (existingTask.getStatus() != TaskStatus.DRAFT && existingTask.getStatus() != TaskStatus.OPEN) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        validateRewardAmount(request.getRewardType(), request.getRewardAmount());
        if (existingTask.getStatus() == TaskStatus.OPEN) {
            contentModerationService.assertTaskUpdateAllowed(
                    operatorId, request, existingTask.getSensitiveReason());
        }

        existingTask.setTitle(request.getTitle());
        existingTask.setDescription(request.getDescription());
        existingTask.setDeadline(request.getDeadline());
        existingTask.setRewardType(request.getRewardType());
        existingTask.setRewardDescription(request.getRewardDescription());
        existingTask.setRewardAmount(request.getRewardAmount());
        existingTask.setCompletionStandard(request.getCompletionStandard());
        existingTask.setRequiresProof(toInt(request.getRequiresProof()));
        existingTask.setOnlyInAppContact(toInt(request.getOnlyInAppContact()));
        existingTask.setAllowOfflineContact(toInt(request.getAllowOfflineContact()));
        LocalDateTime now = LocalDateTime.now();
        existingTask.setUpdatedAt(now);

        int affectedRows = taskMapper.updateById(existingTask);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "Failed to update task");
        }

        taskHistoryService.recordHistory(
                taskId,
                existingTask.getStatus(),
                existingTask.getStatus(),
                operatorId,
                TaskHistoryEventType.UPDATED, null);

        return taskMapper.selectDetailById(existingTask.getId());
    }

    @Override
    public TaskView cancelTask(Long operatorId, Long taskId, String reason) {
        requireOperatorId(operatorId);
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId must not be null");
        }

        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        if (!operatorId.equals(task.getRequesterId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        TaskStatus fromStatus = task.getStatus();
        TaskStatusTransitionAction action = TaskStatusTransitionAction.CANCEL;
        TaskStatus toStatus = resolveTargetStatus(fromStatus, action);

        if (fromStatus != TaskStatus.OPEN && (reason == null || reason.isBlank())) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "reason is required for cancellation");
        }

        String normalizedReason = isBlank(reason) ? "发布者取消了待接单任务" : reason.trim();

        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, fromStatus, toStatus, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        taskHistoryService.recordHistory(
                taskId,
                fromStatus,
                toStatus,
                operatorId,
                action.getHistoryEventType(), normalizedReason);

        if (fromStatus == TaskStatus.IN_PROGRESS) {
            creditService.adjustCredit(
                    operatorId,
                    -1,
                    "进行中的任务 #" + taskId + " 被发布者取消",
                    CreditSourceType.TASK,
                    taskId);
        }
        notificationService.notifyTaskStatusChanged(
                task,
                toStatus,
                operatorId,
                normalizedReason);

        task.setStatus(toStatus);
        task.setUpdatedAt(now);
        return taskMapper.selectDetailById(task.getId());
    }

    @Override
    public TaskView withdrawFromTask(Long providerId, Long taskId, String reason) {
        requireOperatorId(providerId);
        if (taskId == null || isBlank(reason)) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "taskId and reason are required");
        }

        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }
        if (task.getProviderId() == null || !providerId.equals(task.getProviderId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        TaskStatus fromStatus = task.getStatus();
        if (fromStatus != TaskStatus.ACCEPTED && fromStatus != TaskStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        LocalDateTime now = LocalDateTime.now();
        if (taskMapper.releaseProviderById(taskId, providerId, fromStatus, now) != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        TaskApplication application =
                taskApplicationMapper.selectByTaskIdAndApplicantId(taskId, providerId);
        if (application != null && application.getStatus() == ApplicationStatus.ACCEPTED) {
            int applicationUpdated = taskApplicationMapper.updateStatusById(
                    application.getId(),
                    ApplicationStatus.ACCEPTED,
                    ApplicationStatus.WITHDRAWN,
                    now);
            if (applicationUpdated != 1) {
                throw new BusinessException(
                        ErrorCode.COMMON_003_CONFLICT,
                        "Accepted application changed before provider withdrawal");
            }
        }

        String normalizedReason = reason.trim();
        taskHistoryService.recordHistory(
                taskId,
                fromStatus,
                TaskStatus.OPEN,
                providerId,
                TaskHistoryEventType.PROVIDER_WITHDRAWN, normalizedReason);
        if (fromStatus == TaskStatus.IN_PROGRESS) {
            creditService.adjustCredit(
                    providerId,
                    -1,
                    "退出进行中的任务 #" + taskId,
                    CreditSourceType.TASK,
                    taskId);
        }
        notificationService.notifyTaskStatusChanged(
                task,
                TaskStatus.OPEN,
                providerId,
                "接单者退出任务：" + normalizedReason);

        task.setProviderId(null);
        task.setStatus(TaskStatus.OPEN);
        task.setUpdatedAt(now);
        return taskMapper.selectDetailById(task.getId());
    }

    @Override
    public void deleteDraft(Long operatorId, Long taskId) {
        requireOperatorId(operatorId);
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId must not be null");
        }

        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }
        if (!operatorId.equals(task.getRequesterId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }
        if (task.getStatus() != TaskStatus.DRAFT) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        taskHistoryService.deleteByTaskId(taskId);
        if (taskMapper.deleteById(taskId) != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "Failed to delete draft");
        }
    }

    @Override
    public TaskView saveDraft(Long requesterId, SaveTaskDraftRequest request) {
        requireRequesterId(requesterId);
        requireVerifiedUser(requesterId);

        validateRewardAmount(request.getRewardType(), request.getRewardAmount());

        Task task = buildDraftTask(requesterId, request);
        int affectedRows = taskMapper.insert(task);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "Failed to save draft");
        }

        taskHistoryService.recordHistory(
                task.getId(),
                null,
                task.getStatus(),
                requesterId,
                TaskHistoryEventType.DRAFT_SAVED, null);

        return TaskView.from(task);
    }

    @Override
    public TaskView resendTask(Long operatorId, Long taskId, CreateTaskRequest request) {
        requireOperatorId(operatorId);
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId must not be null");
        }

        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        if (!operatorId.equals(task.getRequesterId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        if (task.getStatus() != TaskStatus.CANCELLED && task.getStatus() != TaskStatus.DRAFT) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        requireVerifiedUser(operatorId);
        validateRewardAmount(request.getRewardType(), request.getRewardAmount());
        contentModerationService.assertTaskPublishAllowed(operatorId, request);

        Task republished = buildTask(operatorId, request);
        republished.setId(taskId);
        republished.setCreatedAt(task.getCreatedAt());
        int affectedRows = taskMapper.update(republished, Wrappers.<Task>lambdaUpdate()
                .eq(Task::getId, taskId)
                .eq(Task::getStatus, task.getStatus()));
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        taskHistoryService.recordHistory(
                taskId,
                task.getStatus(),
                republished.getStatus(),
                operatorId,
                TaskHistoryEventType.REPOSTED, null);
        return taskMapper.selectDetailById(taskId);
    }

    @Override
    public TaskView transitionTaskStatus(
            Long operatorId,
            Long taskId,
            TaskStatusTransitionRequest request) {
        validateTransitionIds(operatorId, taskId);
        validateTransitionReason(request);
        if (request.getAction() == TaskStatusTransitionAction.CANCEL) {
            return cancelTask(operatorId, taskId, request.getReason());
        }

        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        TaskStatus fromStatus = task.getStatus();
        TaskStatus toStatus = resolveTargetStatus(fromStatus, request.getAction());
        validateTransitionPermission(task, operatorId, request.getAction());

        LocalDateTime updatedAt = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, fromStatus, toStatus, updatedAt);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        if (toStatus == TaskStatus.COMPLETED) {
            creditService.awardTaskReward(task);
        }

        taskHistoryService.recordHistory(
                taskId,
                fromStatus,
                toStatus,
                operatorId,
                request.getAction().getHistoryEventType(), request.getReason());
        notificationService.notifyTaskStatusChanged(
                task,
                toStatus,
                operatorId,
                request.getReason());

        task.setStatus(toStatus);
        task.setUpdatedAt(updatedAt);
        return taskMapper.selectDetailById(task.getId());
    }

    private void requireVerifiedUser(Long requesterId) {
        CampusVerification verification =
                campusVerificationMapper.selectLatestByUserId(requesterId);
        if (verification == null
                || verification.getStatus() != VerificationStatus.VERIFIED) {
            throw new BusinessException(ErrorCode.USER_002_NOT_VERIFIED);
        }
    }

    private Task buildTask(Long requesterId, CreateTaskRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Task task = new Task();
        task.setRequesterId(requesterId);
        task.setProviderId(null);
        task.setCategory(request.getCategory());
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setCampus(request.getCampus());
        task.setLocation(request.getLocation());
        task.setDeliveryLocation(request.getDeliveryLocation());
        task.setDeadline(request.getDeadline());
        task.setRewardType(request.getRewardType());
        task.setRewardDescription(request.getRewardDescription());
        task.setRewardAmount(request.getRewardAmount());
        task.setCompletionStandard(request.getCompletionStandard());
        task.setRequiresProof(toInt(request.getRequiresProof()));
        task.setOnlyInAppContact(toInt(defaultBoolean(request.getOnlyInAppContact(), true)));
        task.setAllowOfflineContact(toInt(defaultBoolean(request.getAllowOfflineContact(), false)));
        task.setIsInstantTask(toInt(defaultBoolean(request.getIsInstantTask(), false)));
        task.setRouteHint(request.getRouteHint());
        task.setRiskDisclosureConfirmed(
                toInt(defaultBoolean(request.getRiskDisclosureConfirmed(), false)));
        task.setSensitiveReason(request.getSensitiveReason());
        task.setStatus(TaskStatus.OPEN);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    private Boolean defaultBoolean(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int toInt(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void throwValidation(String message) {
        throw new BusinessException(
                ErrorCode.COMMON_001_VALIDATION_ERROR,
                message);
    }

    private void validateRewardAmount(RewardType rewardType, Integer rewardAmount) {
        if (rewardType != null && !rewardType.isAmountWithinLimit(rewardAmount)) {
            throwValidation("points rewardAmount must be less than or equal to " + rewardType.getMaxAmount());
        }
    }

    private void validateTransitionIds(Long operatorId, Long taskId) {
        if (operatorId == null) {
            throwValidation("operatorId must not be null");
        }
        if (taskId == null) {
            throwValidation("taskId must not be null");
        }
    }

    private TaskStatus resolveTargetStatus(TaskStatus fromStatus, TaskStatusTransitionAction action) {
        if (!action.canTransitionFrom(fromStatus)) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }
        return action.getTargetStatus();
    }

    private void validateTransitionPermission(
            Task task,
            Long operatorId,
            TaskStatusTransitionAction action) {
        boolean permitted = switch (action) {
            case START -> operatorId.equals(task.getProviderId());
            case CONFIRM_COMPLETION, REJECT_COMPLETION, CANCEL -> operatorId.equals(task.getRequesterId());
            case DISPUTE -> operatorId.equals(task.getRequesterId()) || operatorId.equals(task.getProviderId());
        };
        if (!permitted) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }
    }

    private void validateTransitionReason(TaskStatusTransitionRequest request) {
        if (request.getAction().isReasonRequired() && isBlank(request.getReason())) {
            throwValidation("reason is required");
        }
    }

    private void requireOperatorId(Long operatorId) {
        if (operatorId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "operatorId must not be null");
        }
    }

    private Task buildDraftTask(Long requesterId, SaveTaskDraftRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Task task = new Task();
        task.setRequesterId(requesterId);
        task.setProviderId(null);
        task.setCategory(request.getCategory());
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setCampus(request.getCampus());
        task.setLocation(request.getLocation());
        task.setDeliveryLocation(request.getDeliveryLocation());
        task.setDeadline(request.getDeadline());
        task.setRewardType(request.getRewardType());
        task.setRewardDescription(request.getRewardDescription());
        task.setRewardAmount(request.getRewardAmount());
        task.setCompletionStandard(request.getCompletionStandard());
        task.setRequiresProof(request.getRequiresProof() != null ? toInt(request.getRequiresProof()) : 0);
        task.setOnlyInAppContact(toInt(defaultBoolean(request.getOnlyInAppContact(), true)));
        task.setAllowOfflineContact(toInt(defaultBoolean(request.getAllowOfflineContact(), false)));
        task.setIsInstantTask(toInt(defaultBoolean(request.getIsInstantTask(), false)));
        task.setRouteHint(request.getRouteHint());
        task.setRiskDisclosureConfirmed(toInt(defaultBoolean(request.getRiskDisclosureConfirmed(), false)));
        task.setSensitiveReason(request.getSensitiveReason());
        task.setStatus(TaskStatus.DRAFT);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }
}
