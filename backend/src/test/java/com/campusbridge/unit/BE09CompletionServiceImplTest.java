package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.CompletionProofMapper;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.pojo.CompletionProof;
import com.campusbridge.pojo.Attachment;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.TaskHistoryService;
import com.campusbridge.service.impl.CompletionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BE09CompletionServiceImplTest {
    private static final Long REQUESTER_ID = 1L;
    private static final Long PROVIDER_ID = 2L;
    private static final Long TASK_ID = 10L;

    @Mock
    private CompletionProofMapper completionProofMapper;

    @Mock
    private AttachmentMapper attachmentMapper;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private TaskHistoryService taskHistoryService;

    @Mock
    private CreditService creditService;

    @Mock
    private NotificationService notificationService;

    private CompletionServiceImpl completionService;

    @BeforeEach
    void setUp() {
        completionService = new CompletionServiceImpl(
                completionProofMapper,
                attachmentMapper,
                taskMapper,
                taskHistoryService,
                creditService,
                notificationService);
    }

    // ---- submitCompletionProof ----

    @Test
    void submitProofTransitionsInProgressToPendingConfirmation() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "IN_PROGRESS", "POINTS", 20);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(completionProofMapper.insert(any(CompletionProof.class))).thenReturn(1);
        when(taskMapper.updateStatusById(
                eq(TASK_ID), eq("IN_PROGRESS"), eq("PENDING_CONFIRMATION"), any()))
                .thenReturn(1);

        CompletionProof proof = completionService.submitCompletionProof(
                PROVIDER_ID, TASK_ID, submitRequest("已完成任务"));

        assertThat(proof.getTaskId()).isEqualTo(TASK_ID);
        assertThat(proof.getSubmitterId()).isEqualTo(PROVIDER_ID);
        verify(taskHistoryService).recordHistory(TASK_ID,
                "IN_PROGRESS", "PENDING_CONFIRMATION", PROVIDER_ID,
                "COMPLETION_PROOF_SUBMITTED");
        verify(notificationService).notifyTaskStatusChanged(
                eq(task), eq("IN_PROGRESS"), eq("PENDING_CONFIRMATION"), eq(PROVIDER_ID), anyString());
    }

    @Test
    void submitProofRejectsNonProvider() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "IN_PROGRESS", "POINTS", 20);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> completionService.submitCompletionProof(
                999L, TASK_ID, submitRequest("done")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_003_PERMISSION_DENIED);
    }

    @Test
    void submitProofRejectsWrongStatus() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "ACCEPTED", "POINTS", 20);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> completionService.submitCompletionProof(
                PROVIDER_ID, TASK_ID, submitRequest("done")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
    }

    @Test
    void submitProofRejectsNullDescription() {
        assertThatThrownBy(() -> completionService.submitCompletionProof(
                PROVIDER_ID, TASK_ID, submitRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_001_VALIDATION_ERROR);
    }

    @Test
    void submitProofBindsOwnedTemporaryAttachments() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "IN_PROGRESS", "POINTS", 20);
        Attachment attachment = new Attachment();
        attachment.setId(30L);
        attachment.setUploaderId(PROVIDER_ID);
        attachment.setOwnerType("COMPLETION_PROOF");
        attachment.setStatus("TEMPORARY");
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(completionProofMapper.insert(any(CompletionProof.class))).thenAnswer(invocation -> {
            CompletionProof proof = invocation.getArgument(0);
            proof.setId(40L);
            return 1;
        });
        when(attachmentMapper.selectById(30L)).thenReturn(attachment);
        when(attachmentMapper.updateById(attachment)).thenReturn(1);
        when(taskMapper.updateStatusById(
                eq(TASK_ID), eq("IN_PROGRESS"), eq("PENDING_CONFIRMATION"), any()))
                .thenReturn(1);
        RequestDtos.SubmitCompletionRequest request = submitRequest("已完成并附图");
        request.setAttachmentIds(List.of(30L));

        completionService.submitCompletionProof(PROVIDER_ID, TASK_ID, request);

        assertThat(attachment.getOwnerId()).isEqualTo(40L);
        assertThat(attachment.getStatus()).isEqualTo("ACTIVE");
        verify(attachmentMapper).updateById(attachment);
    }

    // ---- confirmCompletion ----

    @Test
    void confirmCompletionSettlesPointsReward() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "PENDING_CONFIRMATION", "POINTS", 20);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskMapper.updateStatusById(
                eq(TASK_ID), eq("PENDING_CONFIRMATION"), eq("COMPLETED"), any()))
                .thenReturn(1);

        completionService.confirmCompletion(REQUESTER_ID, TASK_ID);

        verify(creditService).adjustCredit(
                PROVIDER_ID, 20, "任务 #10 积分报酬收入", "TASK", TASK_ID);
        verify(taskHistoryService).recordHistory(TASK_ID,
                "PENDING_CONFIRMATION", "COMPLETED", REQUESTER_ID,
                "COMPLETION_CONFIRMED");
        verify(notificationService).notifyTaskStatusChanged(
                eq(task), eq("PENDING_CONFIRMATION"), eq("COMPLETED"), eq(REQUESTER_ID), anyString());
    }

    @Test
    void confirmCompletionRejectsNonRequester() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "PENDING_CONFIRMATION", "POINTS", 20);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> completionService.confirmCompletion(PROVIDER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_003_PERMISSION_DENIED);
    }

    @Test
    void confirmCompletionSkipsSettlementForNonPointsReward() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "PENDING_CONFIRMATION", "CASH", 50);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskMapper.updateStatusById(
                eq(TASK_ID), eq("PENDING_CONFIRMATION"), eq("COMPLETED"), any()))
                .thenReturn(1);

        completionService.confirmCompletion(REQUESTER_ID, TASK_ID);

        verify(creditService, never())
                .adjustCredit(anyLong(), anyInt(), any(), any(), any());
    }

    // ---- rejectCompletion ----

    @Test
    void rejectCompletionReturnsToInProgress() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "PENDING_CONFIRMATION", "POINTS", 20);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskMapper.updateStatusById(
                eq(TASK_ID), eq("PENDING_CONFIRMATION"), eq("IN_PROGRESS"), any()))
                .thenReturn(1);

        completionService.rejectCompletion(REQUESTER_ID, TASK_ID, "凭证不充分");

        verify(taskHistoryService).recordHistory(TASK_ID,
                "PENDING_CONFIRMATION", "IN_PROGRESS", REQUESTER_ID,
                "COMPLETION_REJECTED; REASON=凭证不充分");
        verify(notificationService).notifyTaskStatusChanged(
                eq(task), eq("PENDING_CONFIRMATION"), eq("IN_PROGRESS"), eq(REQUESTER_ID), anyString());
    }

    @Test
    void rejectCompletionRequiresReason() {
        assertThatThrownBy(() -> completionService.rejectCompletion(REQUESTER_ID, TASK_ID, "  "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_001_VALIDATION_ERROR);
    }

    // ---- disputeCompletion ----

    @Test
    void disputeCompletionMarksTaskDisputed() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "PENDING_CONFIRMATION", "POINTS", 20);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskMapper.updateStatusById(
                eq(TASK_ID), eq("PENDING_CONFIRMATION"), eq("DISPUTED"), any()))
                .thenReturn(1);

        completionService.disputeCompletion(REQUESTER_ID, TASK_ID, "任务未按要求完成");

        verify(taskHistoryService).recordHistory(TASK_ID,
                "PENDING_CONFIRMATION", "DISPUTED", REQUESTER_ID,
                "COMPLETION_DISPUTED; REASON=任务未按要求完成");
        verify(notificationService).notifyTaskStatusChanged(
                eq(task), eq("PENDING_CONFIRMATION"), eq("DISPUTED"), eq(REQUESTER_ID), anyString());
    }

    @Test
    void disputeCompletionRejectsNonParticipant() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "IN_PROGRESS", "POINTS", 20);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> completionService.disputeCompletion(999L, TASK_ID, "reason"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_003_PERMISSION_DENIED);
    }

    @Test
    void disputeCompletionRejectsWrongStatus() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "COMPLETED", "POINTS", 20);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> completionService.disputeCompletion(REQUESTER_ID, TASK_ID, "reason"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
    }

    // ---- helpers ----

    private Task task(Long id, Long requesterId, Long providerId,
                      String status, String rewardType, Integer rewardAmount) {
        Task task = new Task();
        task.setId(id);
        task.setRequesterId(requesterId);
        task.setProviderId(providerId);
        task.setStatus(status);
        task.setRewardType(rewardType);
        task.setRewardAmount(rewardAmount);
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    private RequestDtos.SubmitCompletionRequest submitRequest(String description) {
        RequestDtos.SubmitCompletionRequest request = new RequestDtos.SubmitCompletionRequest();
        request.setDescription(description);
        return request;
    }
}
