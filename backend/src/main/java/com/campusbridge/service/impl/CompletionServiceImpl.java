package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.SubmitCompletionRequest;
import com.campusbridge.entity.Attachment;
import com.campusbridge.entity.CompletionProof;
import com.campusbridge.entity.Task;
import com.campusbridge.enums.AttachmentOwnerType;
import com.campusbridge.enums.AttachmentStatus;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.enums.TaskHistoryEventType;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.CompletionProofMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.service.CompletionService;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.TaskNotificationService;
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

    private final CompletionProofMapper completionProofMapper;
    private final AttachmentMapper attachmentMapper;
    private final TaskMapper taskMapper;
    private final TaskHistoryService taskHistoryService;
    private final CreditService creditService;
    private final TaskNotificationService notificationService;

    @Override
    public CompletionProof submitCompletionProof(
            Long submitterId,
            Long taskId,
            SubmitCompletionRequest request) {
        validateSubmitterId(submitterId);
        validateTaskId(taskId);

        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        // 验证提交者必须是任务的provider
        if (!submitterId.equals(task.getProviderId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        // 验证任务状态必须是 IN_PROGRESS
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
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
        int statusAffected = taskMapper.updateStatusById(taskId, TaskStatus.IN_PROGRESS, TaskStatus.PENDING_CONFIRMATION, now);
        if (statusAffected != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 记录历史
        taskHistoryService.recordHistory(
                taskId,
                TaskStatus.IN_PROGRESS,
                TaskStatus.PENDING_CONFIRMATION,
                submitterId,
                TaskHistoryEventType.COMPLETION_PROOF_SUBMITTED, null);
        notificationService.notifyTaskStatusChanged(
                task,
                TaskStatus.PENDING_CONFIRMATION,
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
                    attachmentId, submitterId, AttachmentOwnerType.COMPLETION_PROOF, proofId) != 1) {
                throw new BusinessException(
                        ErrorCode.COMMON_999_INTERNAL_ERROR,
                        "Failed to bind completion attachment");
            }
        }
    }

    private void validateAttachmentOwnership(Attachment attachment, Long submitterId) {
        if (attachment == null
                || !submitterId.equals(attachment.getUploaderId())
                || attachment.getOwnerType() != AttachmentOwnerType.COMPLETION_PROOF
                || attachment.getOwnerId() != null
                || attachment.getStatus() != AttachmentStatus.TEMPORARY) {
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
        if (task.getStatus() != TaskStatus.PENDING_CONFIRMATION) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 更新任务状态为 COMPLETED
        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, TaskStatus.PENDING_CONFIRMATION, TaskStatus.COMPLETED, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 积分报酬结算
        creditService.awardTaskReward(task);

        // 记录历史
        taskHistoryService.recordHistory(
                taskId,
                TaskStatus.PENDING_CONFIRMATION,
                TaskStatus.COMPLETED,
                requesterId,
                TaskHistoryEventType.COMPLETION_CONFIRMED, null);
        notificationService.notifyTaskStatusChanged(
                task,
                TaskStatus.COMPLETED,
                requesterId,
                "发布者已确认收到任务成果。");
    }

    @Override
    public void rejectCompletion(Long requesterId, Long taskId, String reason) {
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
        if (task.getStatus() != TaskStatus.PENDING_CONFIRMATION) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 更新任务状态回到 IN_PROGRESS（可以重新提交）
        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, TaskStatus.PENDING_CONFIRMATION, TaskStatus.IN_PROGRESS, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 记录历史（包含驳回原因）
        taskHistoryService.recordHistory(
                taskId,
                TaskStatus.PENDING_CONFIRMATION,
                TaskStatus.IN_PROGRESS,
                requesterId,
                TaskHistoryEventType.COMPLETION_REJECTED, reason);
        notificationService.notifyTaskStatusChanged(
                task,
                TaskStatus.IN_PROGRESS,
                requesterId,
                "完成凭证被驳回：" + reason);
    }

    @Override
    public void disputeCompletion(Long participantId, Long taskId, String reason) {
        validateOperatorId(participantId);
        validateTaskId(taskId);

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
        TaskStatus fromStatus = task.getStatus();
        if (fromStatus != TaskStatus.IN_PROGRESS && fromStatus != TaskStatus.PENDING_CONFIRMATION) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 更新任务状态为 DISPUTED
        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, fromStatus, TaskStatus.DISPUTED, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        // 记录历史（包含争议原因）
        taskHistoryService.recordHistory(
                taskId,
                fromStatus,
                TaskStatus.DISPUTED,
                participantId,
                TaskHistoryEventType.DISPUTED, reason);
        notificationService.notifyTaskStatusChanged(
                task,
                TaskStatus.DISPUTED,
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
}
