package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.pojo.CampusVerification;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.ContentModerationService;
import com.campusbridge.service.TaskHistoryService;
import com.campusbridge.service.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Transactional
@Service
public class TaskServiceImpl implements TaskService {
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_CAMPUS_LENGTH = 50;
    private static final int MAX_LOCATION_LENGTH = 120;
    private static final int MAX_REWARD_DESCRIPTION_LENGTH = 200;
    private static final int MAX_COMPLETION_STANDARD_LENGTH = 500;
    private static final int MAX_SENSITIVE_REASON_LENGTH = 300;
    private static final int MAX_ATTACHMENT_COUNT = 9;
    private static final int MAX_TRANSITION_REASON_LENGTH = 300;

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String VERIFICATION_STATUS_VERIFIED = "VERIFIED";
    private static final String HISTORY_NOTE_CREATED = "TASK_CREATED";
    private static final String HISTORY_NOTE_UPDATED = "TASK_UPDATED";
    private static final String HISTORY_NOTE_CANCELLED = "TASK_CANCELLED";
    private static final String HISTORY_NOTE_DRAFT_SAVED = "DRAFT_SAVED";
    private static final String HISTORY_NOTE_RESENT = "TASK_RESENT";

    private final TaskMapper taskMapper;
    private final CampusVerificationMapper campusVerificationMapper;
    private final TaskHistoryService taskHistoryService;
    private final ContentModerationService contentModerationService;

    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_PENDING_CONFIRMATION = "PENDING_CONFIRMATION";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_DISPUTED = "DISPUTED";

    public TaskServiceImpl(
            TaskMapper taskMapper,
            CampusVerificationMapper campusVerificationMapper,
            TaskHistoryService taskHistoryService,
            ContentModerationService contentModerationService) {
        this.taskMapper = taskMapper;
        this.campusVerificationMapper = campusVerificationMapper;
        this.taskHistoryService = taskHistoryService;
        this.contentModerationService = contentModerationService;
    }

    @Override
    public Task createTask(Long requesterId, RequestDtos.CreateTaskRequest request) {
        requireRequesterId(requesterId);
        requireVerifiedUser(requesterId);
        validateCreateTaskRequest(request);
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
                HISTORY_NOTE_CREATED);
        return task;
    }

    private void requireRequesterId(Long requesterId) {
        if (requesterId == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "requesterId must not be null");
        }
    }

    @Override
    public List<Task> listTasks(
            String keyword,
            String category,
            String campus,
            String status,
            String rewardType,
            Boolean requiresProof,
            String sortBy,
            Integer page,
            Integer size) {
        int pageNum = page != null && page > 0 ? page : 1;
        int pageSize = size != null && size > 0 && size <= 100 ? size : 20;
        int offset = (pageNum - 1) * pageSize;

        Map<String, Object> params = new HashMap<>();
        params.put("keyword", keyword);
        params.put("category", category);
        params.put("campus", campus);
        params.put("status", status);
        params.put("rewardType", rewardType);
        params.put("requiresProof", requiresProof);
        params.put("sortBy", sortBy);
        params.put("offset", offset);
        params.put("limit", pageSize);

        return taskMapper.selectWithFilters(params);
    }

    @Override
    public Task getTaskDetail(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId must not be null");
        }
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }
        return task;
    }

    @Override
    public Task updateTask(Long operatorId, Long taskId, RequestDtos.UpdateTaskRequest request) {
        requireOperatorId(operatorId);
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId must not be null");
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "Request body must not be null");
        }

        Task existingTask = taskMapper.selectById(taskId);
        if (existingTask == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        if (!operatorId.equals(existingTask.getRequesterId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        if (!STATUS_DRAFT.equals(existingTask.getStatus()) && !STATUS_OPEN.equals(existingTask.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        validateUpdateTaskRequest(request);

        applyUpdateToTask(existingTask, request);
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
                HISTORY_NOTE_UPDATED);

        return existingTask;
    }

    @Override
    public Task cancelTask(Long operatorId, Long taskId, String reason) {
        requireOperatorId(operatorId);
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId must not be null");
        }

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        if (!operatorId.equals(task.getRequesterId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        String fromStatus = task.getStatus();
        if (!STATUS_OPEN.equals(fromStatus)
                && !STATUS_ACCEPTED.equals(fromStatus)
                && !STATUS_IN_PROGRESS.equals(fromStatus)) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "reason is required for cancellation");
        }

        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, fromStatus, STATUS_CANCELLED, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        taskHistoryService.recordHistory(
                taskId,
                fromStatus,
                STATUS_CANCELLED,
                operatorId,
                "ACTION=CANCEL; REASON=" + reason);

        task.setStatus(STATUS_CANCELLED);
        task.setUpdatedAt(now);
        return task;
    }

    @Override
    public Task saveDraft(Long requesterId, RequestDtos.CreateTaskRequest request) {
        requireRequesterId(requesterId);
        requireVerifiedUser(requesterId);

        if (request == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "Request body must not be null");
        }

        validateDraftRequest(request);

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
                HISTORY_NOTE_DRAFT_SAVED);

        return task;
    }

    @Override
    public Task resendTask(Long operatorId, Long taskId) {
        requireOperatorId(operatorId);
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId must not be null");
        }

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        if (!operatorId.equals(task.getRequesterId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        if (!STATUS_CANCELLED.equals(task.getStatus()) && !STATUS_DRAFT.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        contentModerationService.assertTaskPublishAllowed(operatorId, buildResendRequest(task));

        String fromStatus = task.getStatus();
        String toStatus = resolveInitialStatusForResend(task);
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
                HISTORY_NOTE_RESENT);

        task.setStatus(toStatus);
        task.setUpdatedAt(now);
        return task;
    }

    @Override
    public Task transitionTaskStatus(
            Long operatorId,
            Long taskId,
            RequestDtos.TaskStatusTransitionRequest request) {
        validateTransitionRequest(operatorId, taskId, request);

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        String fromStatus = task.getStatus();
        String toStatus = resolveTargetStatus(fromStatus, request.getAction());
        validateTransitionPermission(task, operatorId, request.getAction());
        validateTransitionReason(request);

        LocalDateTime updatedAt = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, fromStatus, toStatus, updatedAt);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        taskHistoryService.recordHistory(
                taskId,
                fromStatus,
                toStatus,
                operatorId,
                buildTransitionHistoryNote(request));

        task.setStatus(toStatus);
        task.setUpdatedAt(updatedAt);
        return task;
    }

    private void requireVerifiedUser(Long requesterId) {
        CampusVerification verification =
                campusVerificationMapper.selectLatestByUserId(requesterId);
        if (verification == null
                || !VERIFICATION_STATUS_VERIFIED.equals(verification.getStatus())) {
            throw new BusinessException(ErrorCode.USER_002_NOT_VERIFIED);
        }
    }

    private void validateCreateTaskRequest(RequestDtos.CreateTaskRequest request) {
        if (request == null) {
            throwValidation("Request body must not be null");
        }

        requireNotNull(request.getCategory(), "category is required");
        requireNotBlank(request.getTitle(), "title is required");
        requireNotBlank(request.getDescription(), "description is required");
        requireNotBlank(request.getCampus(), "campus is required");
        requireNotBlank(request.getLocation(), "location is required");
        requireNotNull(request.getDeadline(), "deadline is required");
        requireNotNull(request.getRewardType(), "rewardType is required");
        requireNotBlank(request.getCompletionStandard(), "completionStandard is required");
        requireNotNull(request.getRequiresProof(), "requiresProof is required");

        validateLength(request.getTitle(), MAX_TITLE_LENGTH, "title is too long");
        validateLength(request.getDescription(), MAX_DESCRIPTION_LENGTH, "description is too long");
        validateLength(request.getCampus(), MAX_CAMPUS_LENGTH, "campus is too long");
        validateLength(request.getLocation(), MAX_LOCATION_LENGTH, "location is too long");
        validateLength(
                request.getRewardDescription(),
                MAX_REWARD_DESCRIPTION_LENGTH,
                "rewardDescription is too long");
        validateLength(
                request.getCompletionStandard(),
                MAX_COMPLETION_STANDARD_LENGTH,
                "completionStandard is too long");
        validateLength(
                request.getSensitiveReason(),
                MAX_SENSITIVE_REASON_LENGTH,
                "sensitiveReason is too long");
        validateAttachmentIds(request.getAttachmentIds());
        validateDeadline(request.getDeadline());
        validateSensitiveTask(request);
    }

    private void validateDeadline(LocalDateTime deadline) {
        if (!deadline.isAfter(LocalDateTime.now())) {
            throwValidation("deadline must be in the future");
        }
    }

    private void validateSensitiveTask(RequestDtos.CreateTaskRequest request) {
        if (request.getCategory() != ApiEnums.TaskCategory.ATTENDANCE_ASSIST) {
            return;
        }
        if (!Boolean.TRUE.equals(request.getRiskDisclosureConfirmed())
                || isBlank(request.getSensitiveReason())) {
            throw new BusinessException(ErrorCode.TASK_005_SENSITIVE_REVIEW_REQUIRED);
        }
    }

    private void validateAttachmentIds(List<Long> attachmentIds) {
        if (attachmentIds != null && attachmentIds.size() > MAX_ATTACHMENT_COUNT) {
            throwValidation("attachmentIds exceeds limit");
        }
    }

    private Task buildTask(Long requesterId, RequestDtos.CreateTaskRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Task task = new Task();
        task.setRequesterId(requesterId);
        task.setProviderId(null);
        task.setCategory(request.getCategory().name());
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setCampus(request.getCampus());
        task.setLocation(request.getLocation());
        task.setDeadline(request.getDeadline());
        task.setRewardType(request.getRewardType().name());
        task.setRewardDescription(request.getRewardDescription());
        task.setCompletionStandard(request.getCompletionStandard());
        task.setRequiresProof(toInt(request.getRequiresProof()));
        task.setOnlyInAppContact(toInt(defaultBoolean(request.getOnlyInAppContact(), true)));
        task.setAllowOfflineContact(toInt(defaultBoolean(request.getAllowOfflineContact(), false)));
        task.setIsInstantTask(toInt(defaultBoolean(request.getIsInstantTask(), false)));
        task.setRouteHint(request.getRouteHint());
        task.setRiskDisclosureConfirmed(
                toInt(defaultBoolean(request.getRiskDisclosureConfirmed(), false)));
        task.setSensitiveReason(request.getSensitiveReason());
        task.setStatus(resolveInitialStatus(request));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    private String resolveInitialStatus(RequestDtos.CreateTaskRequest request) {
        if (request.getCategory() == ApiEnums.TaskCategory.ATTENDANCE_ASSIST) {
            return STATUS_PENDING_REVIEW;
        }
        return STATUS_OPEN;
    }

    private Boolean defaultBoolean(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int toInt(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private void requireNotNull(Object value, String message) {
        if (value == null) {
            throwValidation(message);
        }
    }

    private void requireNotBlank(String value, String message) {
        if (isBlank(value)) {
            throwValidation(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void validateLength(String value, int maxLength, String message) {
        if (value != null && value.length() > maxLength) {
            throwValidation(message);
        }
    }

    private void throwValidation(String message) {
        throw new BusinessException(
                ErrorCode.COMMON_001_VALIDATION_ERROR,
                message);
    }

    private void validateTransitionRequest(
            Long operatorId,
            Long taskId,
            RequestDtos.TaskStatusTransitionRequest request) {
        if (operatorId == null) {
            throwValidation("operatorId must not be null");
        }
        if (taskId == null) {
            throwValidation("taskId must not be null");
        }
        if (request == null) {
            throwValidation("Request body must not be null");
        }
        if (request.getAction() == null) {
            throwValidation("action is required");
        }
        validateLength(
                request.getReason(),
                MAX_TRANSITION_REASON_LENGTH,
                "reason is too long");
    }

    private String resolveTargetStatus(
            String fromStatus,
            ApiEnums.TaskStatusTransitionAction action) {
        switch (action) {
            case START:
                return requireTransition(fromStatus, STATUS_ACCEPTED, STATUS_IN_PROGRESS);
            case CONFIRM_COMPLETION:
                return requireTransition(
                        fromStatus,
                        STATUS_PENDING_CONFIRMATION,
                        STATUS_COMPLETED);
            case REJECT_COMPLETION:
                return requireTransition(
                        fromStatus,
                        STATUS_PENDING_CONFIRMATION,
                        STATUS_IN_PROGRESS);
            case DISPUTE:
                if (STATUS_ACCEPTED.equals(fromStatus)
                        || STATUS_IN_PROGRESS.equals(fromStatus)
                        || STATUS_PENDING_CONFIRMATION.equals(fromStatus)) {
                    return STATUS_DISPUTED;
                }
                throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
            case CANCEL:
                if (STATUS_OPEN.equals(fromStatus)
                        || STATUS_ACCEPTED.equals(fromStatus)
                        || STATUS_IN_PROGRESS.equals(fromStatus)) {
                    return STATUS_CANCELLED;
                }
                throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
            default:
                throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }
    }

    private String requireTransition(
            String fromStatus,
            String expectedFromStatus,
            String toStatus) {
        if (!expectedFromStatus.equals(fromStatus)) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }
        return toStatus;
    }

    private void validateTransitionPermission(
            Task task,
            Long operatorId,
            ApiEnums.TaskStatusTransitionAction action) {
        switch (action) {
            case START:
                requireProvider(task, operatorId);
                break;
            case CONFIRM_COMPLETION:
            case REJECT_COMPLETION:
            case CANCEL:
                requireRequester(task, operatorId);
                break;
            case DISPUTE:
                requireTaskParticipant(task, operatorId);
                break;
            default:
                throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }
    }

    private void requireRequester(Task task, Long operatorId) {
        if (!operatorId.equals(task.getRequesterId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }
    }

    private void requireProvider(Task task, Long operatorId) {
        if (task.getProviderId() == null || !operatorId.equals(task.getProviderId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }
    }

    private void requireTaskParticipant(Task task, Long operatorId) {
        boolean isRequester = operatorId.equals(task.getRequesterId());
        boolean isProvider = task.getProviderId() != null && operatorId.equals(task.getProviderId());
        if (!isRequester && !isProvider) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }
    }

    private void validateTransitionReason(RequestDtos.TaskStatusTransitionRequest request) {
        switch (request.getAction()) {
            case REJECT_COMPLETION:
            case DISPUTE:
            case CANCEL:
                if (isBlank(request.getReason())) {
                    throwValidation("reason is required");
                }
                break;
            default:
                break;
        }
    }

    private String buildTransitionHistoryNote(RequestDtos.TaskStatusTransitionRequest request) {
        if (isBlank(request.getReason())) {
            return "ACTION=" + request.getAction().name();
        }
        return "ACTION=" + request.getAction().name() + "; REASON=" + request.getReason();
    }

    private void requireOperatorId(Long operatorId) {
        if (operatorId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "operatorId must not be null");
        }
    }

    private void validateUpdateTaskRequest(RequestDtos.UpdateTaskRequest request) {
        if (request.getTitle() != null) {
            validateLength(request.getTitle(), MAX_TITLE_LENGTH, "title is too long");
        }
        if (request.getDescription() != null) {
            validateLength(request.getDescription(), MAX_DESCRIPTION_LENGTH, "description is too long");
        }
        if (request.getRewardDescription() != null) {
            validateLength(request.getRewardDescription(), MAX_REWARD_DESCRIPTION_LENGTH, "rewardDescription is too long");
        }
        if (request.getCompletionStandard() != null) {
            validateLength(request.getCompletionStandard(), MAX_COMPLETION_STANDARD_LENGTH, "completionStandard is too long");
        }
        if (request.getDeadline() != null && !request.getDeadline().isAfter(LocalDateTime.now())) {
            throwValidation("deadline must be in the future");
        }
    }

    private void applyUpdateToTask(Task task, RequestDtos.UpdateTaskRequest request) {
        if (request.getTitle() != null) {
            task.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getDeadline() != null) {
            task.setDeadline(request.getDeadline());
        }
        if (request.getRewardType() != null) {
            task.setRewardType(request.getRewardType().name());
        }
        if (request.getRewardDescription() != null) {
            task.setRewardDescription(request.getRewardDescription());
        }
        if (request.getCompletionStandard() != null) {
            task.setCompletionStandard(request.getCompletionStandard());
        }
        if (request.getRequiresProof() != null) {
            task.setRequiresProof(toInt(request.getRequiresProof()));
        }
        if (request.getOnlyInAppContact() != null) {
            task.setOnlyInAppContact(toInt(request.getOnlyInAppContact()));
        }
        if (request.getAllowOfflineContact() != null) {
            task.setAllowOfflineContact(toInt(request.getAllowOfflineContact()));
        }
    }

    private void validateDraftRequest(RequestDtos.CreateTaskRequest request) {
        if (request == null) {
            throwValidation("Request body must not be null");
        }
        if (request.getTitle() != null) {
            validateLength(request.getTitle(), MAX_TITLE_LENGTH, "title is too long");
        }
        if (request.getDescription() != null) {
            validateLength(request.getDescription(), MAX_DESCRIPTION_LENGTH, "description is too long");
        }
    }

    private Task buildDraftTask(Long requesterId, RequestDtos.CreateTaskRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Task task = new Task();
        task.setRequesterId(requesterId);
        task.setProviderId(null);
        task.setCategory(request.getCategory() != null ? request.getCategory().name() : null);
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setCampus(request.getCampus());
        task.setLocation(request.getLocation());
        task.setDeadline(request.getDeadline());
        task.setRewardType(request.getRewardType() != null ? request.getRewardType().name() : null);
        task.setRewardDescription(request.getRewardDescription());
        task.setCompletionStandard(request.getCompletionStandard());
        task.setRequiresProof(request.getRequiresProof() != null ? toInt(request.getRequiresProof()) : 0);
        task.setOnlyInAppContact(toInt(defaultBoolean(request.getOnlyInAppContact(), true)));
        task.setAllowOfflineContact(toInt(defaultBoolean(request.getAllowOfflineContact(), false)));
        task.setIsInstantTask(toInt(defaultBoolean(request.getIsInstantTask(), false)));
        task.setRouteHint(request.getRouteHint());
        task.setRiskDisclosureConfirmed(toInt(defaultBoolean(request.getRiskDisclosureConfirmed(), false)));
        task.setSensitiveReason(request.getSensitiveReason());
        task.setStatus(STATUS_DRAFT);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    private RequestDtos.CreateTaskRequest buildResendRequest(Task task) {
        RequestDtos.CreateTaskRequest request = new RequestDtos.CreateTaskRequest();
        request.setCategory(ApiEnums.TaskCategory.valueOf(task.getCategory()));
        request.setTitle(task.getTitle());
        request.setDescription(task.getDescription());
        request.setCampus(task.getCampus());
        request.setLocation(task.getLocation());
        request.setDeadline(task.getDeadline());
        request.setRewardType(ApiEnums.RewardType.valueOf(task.getRewardType()));
        request.setRewardDescription(task.getRewardDescription());
        request.setCompletionStandard(task.getCompletionStandard());
        request.setRequiresProof(task.getRequiresProof() == 1);
        request.setOnlyInAppContact(task.getOnlyInAppContact() == 1);
        request.setAllowOfflineContact(task.getAllowOfflineContact() == 1);
        request.setIsInstantTask(task.getIsInstantTask() == 1);
        request.setRouteHint(task.getRouteHint());
        request.setRiskDisclosureConfirmed(task.getRiskDisclosureConfirmed() == 1);
        request.setSensitiveReason(task.getSensitiveReason());
        return request;
    }

    private String resolveInitialStatusForResend(Task task) {
        if (ApiEnums.TaskCategory.ATTENDANCE_ASSIST.name().equals(task.getCategory())) {
            return STATUS_PENDING_REVIEW;
        }
        return STATUS_OPEN;
    }
}
