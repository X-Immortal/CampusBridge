package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.CompletionProofMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.pojo.CompletionProof;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.CompletionService;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.TaskHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
@Service
public class CompletionServiceImpl implements CompletionService {
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_PENDING_CONFIRMATION = "PENDING_CONFIRMATION";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_DISPUTED = "DISPUTED";
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_REASON_LENGTH = 500;

    private static final Logger LOGGER = LoggerFactory.getLogger(CompletionServiceImpl.class);

    private final CompletionProofMapper completionProofMapper;
    private final TaskMapper taskMapper;
    private final TaskHistoryService taskHistoryService;
    private final CreditService creditService;

    public CompletionServiceImpl(
            CompletionProofMapper completionProofMapper,
            TaskMapper taskMapper,
            TaskHistoryService taskHistoryService,
            CreditService creditService) {
        this.completionProofMapper = completionProofMapper;
        this.taskMapper = taskMapper;
        this.taskHistoryService = taskHistoryService;
        this.creditService = creditService;
    }

    @Override
    public CompletionProof submitCompletionProof(
            Long submitterId,
            Long taskId,
            RequestDtos.SubmitCompletionRequest request) {
        validateSubmitterId(submitterId);
        validateTaskId(taskId);
        validateSubmitCompletionRequest(request);

        Task task = taskMapper.selectById(taskId);
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

        proof.setSubmittedAt(now);
        return proof;
    }

    @Override
    public void confirmCompletion(Long requesterId, Long taskId) {
        validateOperatorId(requesterId);
        validateTaskId(taskId);

        Task task = taskMapper.selectById(taskId);
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
        settleReward(task);

        // 记录历史
        taskHistoryService.recordHistory(
                taskId,
                STATUS_PENDING_CONFIRMATION,
                STATUS_COMPLETED,
                requesterId,
                "COMPLETION_CONFIRMED");
    }

    private void settleReward(Task task) {
        if (!"POINTS".equals(task.getRewardType())) {
            return;
        }
        Integer amount = task.getRewardAmount();
        if (amount == null || amount <= 0) {
            return;
        }
        Long requesterId = task.getRequesterId();
        Long providerId = task.getProviderId();
        Long taskId = task.getId();

        creditService.adjustCredit(requesterId, -amount,
                "任务 #" + taskId + " 积分报酬支出", "TASK", taskId);
        creditService.adjustCredit(providerId, +amount,
                "任务 #" + taskId + " 积分报酬收入", "TASK", taskId);

        LOGGER.info("Settled {} points for task #{}: {} -> {}", amount, taskId, requesterId, providerId);
    }

    @Override
    public void rejectCompletion(Long requesterId, Long taskId, String reason) {
        validateOperatorId(requesterId);
        validateTaskId(taskId);
        validateReason(reason, true);

        Task task = taskMapper.selectById(taskId);
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
    }

    @Override
    public void disputeCompletion(Long participantId, Long taskId, String reason) {
        validateOperatorId(participantId);
        validateTaskId(taskId);
        validateReason(reason, true);

        Task task = taskMapper.selectById(taskId);
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

    private void validateSubmitCompletionRequest(RequestDtos.SubmitCompletionRequest request) {
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
