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
import com.campusbridge.service.RewardSettlementService;
import com.campusbridge.service.TaskHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
@Service
public class CompletionServiceImpl implements CompletionService {
    private static final Logger log = LoggerFactory.getLogger(CompletionServiceImpl.class);
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_PENDING_CONFIRMATION = "PENDING_CONFIRMATION";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_DISPUTED = "DISPUTED";
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_REASON_LENGTH = 500;

    private final CompletionProofMapper completionProofMapper;
    private final TaskMapper taskMapper;
    private final TaskHistoryService taskHistoryService;
    private final CreditService creditService;
    private final RewardSettlementService rewardSettlementService;

    public CompletionServiceImpl(
            CompletionProofMapper completionProofMapper,
            TaskMapper taskMapper,
            TaskHistoryService taskHistoryService,
            CreditService creditService,
            RewardSettlementService rewardSettlementService) {
        this.completionProofMapper = completionProofMapper;
        this.taskMapper = taskMapper;
        this.taskHistoryService = taskHistoryService;
        this.creditService = creditService;
        this.rewardSettlementService = rewardSettlementService;
    }

    @Override
    public CompletionProof submitCompletionProof(Long submitterId, Long taskId,
                                                  RequestDtos.SubmitCompletionRequest request) {
        validateSubmitterId(submitterId);
        validateTaskId(taskId);
        validateSubmitCompletionRequest(request);

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        if (!submitterId.equals(task.getProviderId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        if (!STATUS_IN_PROGRESS.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        CompletionProof proof = new CompletionProof();
        proof.setTaskId(taskId);
        proof.setSubmitterId(submitterId);
        proof.setDescription(request.getDescription());
        proof.setSubmittedAt(LocalDateTime.now());

        int affectedRows = completionProofMapper.insert(proof);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "Failed to submit completion proof");
        }

        LocalDateTime now = LocalDateTime.now();
        int statusAffected = taskMapper.updateStatusById(taskId, STATUS_IN_PROGRESS, STATUS_PENDING_CONFIRMATION, now);
        if (statusAffected != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        taskHistoryService.recordHistory(
                taskId,
                STATUS_IN_PROGRESS,
                STATUS_PENDING_CONFIRMATION,
                submitterId,
                "COMPLETION_PROOF_SUBMITTED");

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

        if (!requesterId.equals(task.getRequesterId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        if (!STATUS_PENDING_CONFIRMATION.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, STATUS_PENDING_CONFIRMATION, STATUS_COMPLETED, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        taskHistoryService.recordHistory(
                taskId,
                STATUS_PENDING_CONFIRMATION,
                STATUS_COMPLETED,
                requesterId,
                "COMPLETION_CONFIRMED");

        settlePointsReward(task);

        try {
            rewardSettlementService.settleTaskReward(taskId, requesterId, task.getProviderId());
        } catch (Exception e) {
            log.error("Failed to create reward settlement for task {} (requester={}, provider={}): {}",
                    taskId, requesterId, task.getProviderId(), e.getMessage(), e);
        }
    }

    private void settlePointsReward(Task task) {
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

        log.info("Settled {} points for task #{}: requester {} -> provider {}",
                amount, taskId, requesterId, providerId);
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

        if (!requesterId.equals(task.getRequesterId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        if (!STATUS_PENDING_CONFIRMATION.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, STATUS_PENDING_CONFIRMATION, STATUS_IN_PROGRESS, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

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

        boolean isRequester = participantId.equals(task.getRequesterId());
        boolean isProvider = task.getProviderId() != null && participantId.equals(task.getProviderId());

        if (!isRequester && !isProvider) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }

        String fromStatus = task.getStatus();
        if (!STATUS_IN_PROGRESS.equals(fromStatus) && !STATUS_PENDING_CONFIRMATION.equals(fromStatus)) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, fromStatus, STATUS_DISPUTED, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

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
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "description is too long (max " + MAX_DESCRIPTION_LENGTH + " characters)");
        }
    }

    private void validateReason(String reason, boolean required) {
        if (required && (reason == null || reason.isBlank())) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "reason is required");
        }
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "reason is too long (max " + MAX_REASON_LENGTH + " characters)");
        }
    }
}
