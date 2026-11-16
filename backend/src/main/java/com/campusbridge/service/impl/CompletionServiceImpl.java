package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.SubmitCompletionRequest;
import com.campusbridge.entity.Attachment;
import com.campusbridge.entity.CompletionProof;
import com.campusbridge.entity.Task;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.CompletionProofMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.service.CompletionService;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Transactional
@Service
@RequiredArgsConstructor
public class CompletionServiceImpl implements CompletionService {
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_PENDING_CONFIRMATION = "PENDING_CONFIRMATION";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_DISPUTED = "DISPUTED";
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_ATTACHMENT_COUNT = 9;
    private static final String ATTACHMENT_OWNER_TYPE = "COMPLETION_PROOF";
    private static final String ATTACHMENT_STATUS_TEMPORARY = "TEMPORARY";

    private final CompletionProofMapper completionProofMapper;
    private final AttachmentMapper attachmentMapper;
    private final TaskMapper taskMapper;
    private final TaskHistoryService taskHistoryService;
    private final CreditService creditService;
    private final NotificationService notificationService;

    @Override
    public CompletionProof submitCompletionProof(
            Long submitterId,
            Long taskId,
            SubmitCompletionRequest request) {
        validateSubmitterId(submitterId);
        validateTaskId(taskId);
        validateSubmitCompletionRequest(request);

        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        // 验证提交者必须是任务的provider
        if (!submitterId.equals(task.getProviderId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        // 验证任务状态必须是 IN_PROGRESS
        if (!STATUS_IN_PROGRESS.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 创建完成凭证
        CompletionProof proof = new CompletionProof();
        proof.setTaskId(taskId);
        proof.setSubmitterId(submitterId);
        proof.setDescription(request.getDescription());
        proof.setSubmittedAt(LocalDateTime.now());

        int affectedRows = completionProofMapper.insert(proof);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "Failed to submit completion proof");
        }

        bindAttachments(submitterId, proof.getId(), request.getAttachmentIds());

        // 更新任务状态为 PENDING_CONFIRMATION
        LocalDateTime now = LocalDateTime.now();
        int statusAffected = taskMapper.updateStatusById(taskId, STATUS_IN_PROGRESS, STATUS_PENDING_CONFIRMATION, now);
        if (statusAffected != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 记录历史
        taskHistoryService.recordHistory(
                taskId,
                STATUS_IN_PROGRESS,
                STATUS_PENDING_CONFIRMATION,
                submitterId,
                "COMPLETION_PROOF_SUBMITTED");
        notificationService.notifyTaskStatusChanged(
                task,
                STATUS_IN_PROGRESS,
                STATUS_PENDING_CONFIRMATION,
                submitterId,
                "接单者已提交完成凭证，请及时查看并确认。");

        proof.setSubmittedAt(now);
        return proof;
    }

    private void bindAttachments(
            Long submitterId,
            Long proofId,
            List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        if (attachmentIds.size() > MAX_ATTACHMENT_COUNT) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "attachmentIds exceeds limit");
        }

        Set<Long> uniqueIds = new HashSet<>(attachmentIds);
        if (uniqueIds.size() != attachmentIds.size() || uniqueIds.contains(null)) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "attachmentIds must be unique and non-null");
        }

        for (Long attachmentId : attachmentIds.stream().sorted().toList()) {
            Attachment attachment = attachmentMapper.selectById(attachmentId);
            validateAttachmentOwnership(attachment, submitterId);
            if (attachmentMapper.bindTemporaryToOwner(
                    attachmentId, submitterId, ATTACHMENT_OWNER_TYPE, proofId) != 1) {
                throw new BusinessException(
                        ErrorCode.COMMON_999_INTERNAL_ERROR,
                        "Failed to bind completion attachment");
            }
        }
    }

    private void validateAttachmentOwnership(Attachment attachment, Long submitterId) {
        if (attachment == null
                || !submitterId.equals(attachment.getUploaderId())
                || !ATTACHMENT_OWNER_TYPE.equals(attachment.getOwnerType())
                || attachment.getOwnerId() != null
                || !ATTACHMENT_STATUS_TEMPORARY.equals(attachment.getStatus())) {
            throw new BusinessException(
                    ErrorCode.TASK_003_PERMISSION_DENIED,
                    "Completion attachment is unavailable");
        }
    }

    @Override
    public void confirmCompletion(Long requesterId, Long taskId) {
        validateOperatorId(requesterId);
        validateTaskId(taskId);

        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        // 验证操作者必须是requester
        if (!requesterId.equals(task.getRequesterId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        // 验证任务状态必须是 PENDING_CONFIRMATION
        if (!STATUS_PENDING_CONFIRMATION.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 更新任务状态为 COMPLETED
        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, STATUS_PENDING_CONFIRMATION, STATUS_COMPLETED, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 积分报酬结算
        creditService.awardTaskReward(task);

        // 记录历史
        taskHistoryService.recordHistory(
                taskId,
                STATUS_PENDING_CONFIRMATION,
                STATUS_COMPLETED,
                requesterId,
                "COMPLETION_CONFIRMED");
        notificationService.notifyTaskStatusChanged(
                task,
                STATUS_PENDING_CONFIRMATION,
                STATUS_COMPLETED,
                requesterId,
                "发布者已确认收到任务成果。");
    }

    @Override
    public void rejectCompletion(Long requesterId, Long taskId, String reason) {
        validateOperatorId(requesterId);
        validateTaskId(taskId);
        validateReason(reason, true);

        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        // 验证操作者必须是requester
        if (!requesterId.equals(task.getRequesterId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        // 验证任务状态必须是 PENDING_CONFIRMATION
        if (!STATUS_PENDING_CONFIRMATION.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 更新任务状态回到 IN_PROGRESS（可以重新提交）
        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, STATUS_PENDING_CONFIRMATION, STATUS_IN_PROGRESS, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 记录历史（包含驳回原因）
        taskHistoryService.recordHistory(
                taskId,
                STATUS_PENDING_CONFIRMATION,
                STATUS_IN_PROGRESS,
                requesterId,
                "COMPLETION_REJECTED; REASON=" + reason);
        notificationService.notifyTaskStatusChanged(
                task,
                STATUS_PENDING_CONFIRMATION,
                STATUS_IN_PROGRESS,
                requesterId,
                "完成凭证被驳回：" + reason);
    }

    @Override
    public void disputeCompletion(Long participantId, Long taskId, String reason) {
        validateOperatorId(participantId);
        validateTaskId(taskId);
        validateReason(reason, true);

        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        // 验证操作者必须是任务参与者（requester或provider）
        boolean isRequester = participantId.equals(task.getRequesterId());
        boolean isProvider = task.getProviderId() != null && participantId.equals(task.getProviderId());

        if (!isRequester && !isProvider) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        // 验证任务状态：只能在 IN_PROGRESS 或 PENDING_CONFIRMATION 时发起争议
        String fromStatus = task.getStatus();
        if (!STATUS_IN_PROGRESS.equals(fromStatus) && !STATUS_PENDING_CONFIRMATION.equals(fromStatus)) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 更新任务状态为 DISPUTED
        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, fromStatus, STATUS_DISPUTED, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 记录历史（包含争议原因）
        taskHistoryService.recordHistory(
                taskId,
                fromStatus,
                STATUS_DISPUTED,
                participantId,
                "COMPLETION_DISPUTED; REASON=" + reason);
        notificationService.notifyTaskStatusChanged(
                task,
                fromStatus,
                STATUS_DISPUTED,
                participantId,
                "任务参与者发起争议：" + reason);
    }

    private void validateSubmitterId(Long submitterId) {
        if (submitterId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "submitterId must not be null");
        }
    }

    private void validateTaskId(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId must not be null");
        }
    }

    private void validateOperatorId(Long operatorId) {
        if (operatorId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "operatorId must not be null");
        }
    }

    private void validateSubmitCompletionRequest(SubmitCompletionRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "Request body must not be null");
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "description is required");
        }
        if (request.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "description is too long (max " + MAX_DESCRIPTION_LENGTH + " characters)");
        }
    }

    private void validateReason(String reason, boolean required) {
        if (required && (reason == null || reason.isBlank())) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "reason is required");
        }
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "reason is too long (max " + MAX_REASON_LENGTH + " characters)");
        }
    }
}
