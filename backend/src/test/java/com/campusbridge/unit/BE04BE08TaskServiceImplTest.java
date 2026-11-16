package com.campusbridge.unit;

import com.campusbridge.enums.TaskHistoryEventType;
import com.campusbridge.enums.CreditSourceType;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.CreateTaskRequest;
import com.campusbridge.dto.request.SaveTaskDraftRequest;
import com.campusbridge.dto.request.TaskStatusTransitionRequest;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.entity.CampusVerification;
import com.campusbridge.entity.Task;
import com.campusbridge.entity.TaskApplication;
import com.campusbridge.enums.ApplicationStatus;
import com.campusbridge.enums.RewardType;
import com.campusbridge.enums.TaskCategory;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.enums.TaskStatusTransitionAction;
import com.campusbridge.enums.VerificationStatus;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.mapper.TaskApplicationMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.service.ContentModerationService;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.TaskNotificationService;
import com.campusbridge.service.TaskHistoryService;
import com.campusbridge.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BE04BE08TaskServiceImplTest {
    private static final Long REQUESTER_ID = 1L;
    private static final Long PROVIDER_ID = 2L;
    private static final Long TASK_ID = 10L;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private CampusVerificationMapper campusVerificationMapper;

    @Mock
    private TaskHistoryService taskHistoryService;

    @Mock
    private ContentModerationService contentModerationService;

    @Mock
    private TaskNotificationService notificationService;

    @Mock
    private TaskApplicationMapper taskApplicationMapper;

    @Mock
    private CreditService creditService;

    private TaskServiceImpl taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl(
                taskMapper,
                campusVerificationMapper,
                taskHistoryService,
                contentModerationService,
                notificationService,
                taskApplicationMapper,
                creditService);
    }

    @Test
    void createTaskCreatesOpenTaskAndHistoryForVerifiedUser() {
        when(campusVerificationMapper.selectLatestByUserId(REQUESTER_ID))
                .thenReturn(verifiedRecord());
        when(taskMapper.insert(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(TASK_ID);
            return 1;
        });

        TaskView created = taskService.createTask(REQUESTER_ID, normalCreateRequest());

        assertThat(created.getId()).isEqualTo(TASK_ID);
        assertThat(created.getRequesterId()).isEqualTo(REQUESTER_ID);
        assertThat(created.getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(created.getOnlyInAppContact()).isEqualTo(1);
        assertThat(created.getAllowOfflineContact()).isEqualTo(0);
        assertThat(created.getDeliveryLocation()).isEqualTo("5号宿舍楼");
        verify(contentModerationService)
                .assertTaskPublishAllowed(eq(REQUESTER_ID), any(CreateTaskRequest.class));
        verify(taskHistoryService)
                .recordHistory(TASK_ID, null, TaskStatus.OPEN, REQUESTER_ID, TaskHistoryEventType.CREATED, null);
    }

    @Test
    void createTaskRejectsPointsRewardGreaterThanTen() {
        CreateTaskRequest request = normalCreateRequest();
        request.setRewardAmount(11);

        when(campusVerificationMapper.selectLatestByUserId(REQUESTER_ID))
                .thenReturn(verifiedRecord());

        assertThatThrownBy(() -> taskService.createTask(REQUESTER_ID, request))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.COMMON_001_VALIDATION_ERROR));
    }

    @Test
    void createTaskRejectsUnverifiedUser() {
        when(campusVerificationMapper.selectLatestByUserId(REQUESTER_ID))
                .thenReturn(null);

        assertThatThrownBy(() -> taskService.createTask(REQUESTER_ID, normalCreateRequest()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.USER_002_NOT_VERIFIED));
    }

    @Test
    void transitionTaskStatusStartsAcceptedTaskAndWritesHistory() {
        Task acceptedTask = task(TaskStatus.ACCEPTED);
        acceptedTask.setProviderId(PROVIDER_ID);
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(acceptedTask);
        when(taskMapper.selectDetailById(TASK_ID))
                .thenAnswer(invocation -> TaskView.from(acceptedTask));
        when(taskMapper.updateStatusById(
                eq(TASK_ID),
                eq(TaskStatus.ACCEPTED),
                eq(TaskStatus.IN_PROGRESS),
                any(LocalDateTime.class))).thenReturn(1);

        TaskView transitioned = taskService.transitionTaskStatus(
                PROVIDER_ID,
                TASK_ID,
                transitionRequest(TaskStatusTransitionAction.START, null));

        assertThat(transitioned.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        verify(taskHistoryService).recordHistory(
                TASK_ID,
                TaskStatus.ACCEPTED,
                TaskStatus.IN_PROGRESS,
                PROVIDER_ID,
                TaskHistoryEventType.STARTED, null);
    }

    @ParameterizedTest
    @CsvSource({
            "START, ACCEPTED, 1",
            "START, ACCEPTED, 999",
            "CONFIRM_COMPLETION, PENDING_CONFIRMATION, 2",
            "REJECT_COMPLETION, PENDING_CONFIRMATION, 2",
            "DISPUTE, ACCEPTED, 999",
            "CANCEL, OPEN, 2"
    })
    void transitionRejectsUnauthorizedOperator(
            TaskStatusTransitionAction action, TaskStatus status, Long operatorId) {
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task(status));

        assertThatThrownBy(() -> taskService.transitionTaskStatus(
                operatorId, TASK_ID, transitionRequest(action, "reason")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TASK_003_PERMISSION_DENIED));
        org.mockito.Mockito.verifyNoInteractions(taskHistoryService, notificationService, creditService);
        verify(taskMapper, org.mockito.Mockito.never()).updateStatusById(any(), any(), any(), any());
    }

    @Test
    void transitionTaskStatusRejectsIllegalTransition() {
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task(TaskStatus.COMPLETED));

        assertThatThrownBy(() -> taskService.transitionTaskStatus(
                REQUESTER_ID,
                TASK_ID,
                transitionRequest(TaskStatusTransitionAction.CANCEL, "取消测试")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION));
    }

    @Test
    void transitionTaskStatusRequiresReasonForDispute() {
        assertThatThrownBy(() -> taskService.transitionTaskStatus(
                PROVIDER_ID,
                TASK_ID,
                transitionRequest(TaskStatusTransitionAction.DISPUTE, " ")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.COMMON_001_VALIDATION_ERROR));
    }

    @Test
    void requesterCanCancelOpenTaskWithoutReason() {
        Task openTask = task(TaskStatus.OPEN);
        openTask.setProviderId(null);
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(openTask);
        when(taskMapper.selectDetailById(TASK_ID))
                .thenAnswer(invocation -> TaskView.from(openTask));
        when(taskMapper.updateStatusById(
                eq(TASK_ID), eq(TaskStatus.OPEN), eq(TaskStatus.CANCELLED), any()))
                .thenReturn(1);

        TaskView result = taskService.cancelTask(REQUESTER_ID, TASK_ID, null);

        assertThat(result.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        verify(taskHistoryService).recordHistory(TASK_ID, TaskStatus.OPEN, TaskStatus.CANCELLED,
                REQUESTER_ID, TaskHistoryEventType.CANCELLED, "发布者取消了待接单任务");
        verify(notificationService).notifyTaskStatusChanged(
                openTask,
                TaskStatus.CANCELLED,
                REQUESTER_ID,
                "发布者取消了待接单任务");
    }

    @Test
    void requesterCancellingInProgressTaskLosesOnePointAndNotifiesProvider() {
        Task inProgressTask = task(TaskStatus.IN_PROGRESS);
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(inProgressTask);
        when(taskMapper.updateStatusById(
                eq(TASK_ID), eq(TaskStatus.IN_PROGRESS), eq(TaskStatus.CANCELLED), any()))
                .thenReturn(1);

        taskService.cancelTask(REQUESTER_ID, TASK_ID, "临时无法继续任务");

        verify(creditService).adjustCredit(
                REQUESTER_ID,
                -1,
                "进行中的任务 #10 被发布者取消",
                CreditSourceType.TASK,
                TASK_ID);
        verify(notificationService).notifyTaskStatusChanged(
                inProgressTask,
                TaskStatus.CANCELLED,
                REQUESTER_ID,
                "临时无法继续任务");
    }

    @Test
    void providerCanWithdrawAcceptedTaskAndTaskReturnsOpen() {
        Task acceptedTask = task(TaskStatus.ACCEPTED);
        TaskApplication application = new TaskApplication();
        application.setId(20L);
        application.setTaskId(TASK_ID);
        application.setApplicantId(PROVIDER_ID);
        application.setStatus(ApplicationStatus.ACCEPTED);
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(acceptedTask);
        when(taskMapper.selectDetailById(TASK_ID))
                .thenAnswer(invocation -> TaskView.from(acceptedTask));
        when(taskMapper.releaseProviderById(
                eq(TASK_ID), eq(PROVIDER_ID), eq(TaskStatus.ACCEPTED), any()))
                .thenReturn(1);
        when(taskApplicationMapper.selectByTaskIdAndApplicantId(TASK_ID, PROVIDER_ID))
                .thenReturn(application);
        when(taskApplicationMapper.updateStatusById(eq(20L), eq(ApplicationStatus.ACCEPTED), eq(ApplicationStatus.WITHDRAWN), any()))
                .thenReturn(1);

        TaskView result = taskService.withdrawFromTask(PROVIDER_ID, TASK_ID, "时间安排发生变化");

        assertThat(result.getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(result.getProviderId()).isNull();
        verify(taskApplicationMapper).updateStatusById(20L, ApplicationStatus.ACCEPTED, ApplicationStatus.WITHDRAWN, result.getUpdatedAt());
        verify(notificationService).notifyTaskStatusChanged(
                acceptedTask,
                TaskStatus.OPEN,
                PROVIDER_ID,
                "接单者退出任务：时间安排发生变化");
    }

    @Test
    void saveDraftKeepsDeliveryLocation() {
        when(campusVerificationMapper.selectLatestByUserId(REQUESTER_ID))
                .thenReturn(verifiedRecord());
        when(taskMapper.insert(any(Task.class))).thenAnswer(invocation -> {
            Task draft = invocation.getArgument(0);
            draft.setId(TASK_ID);
            return 1;
        });

        SaveTaskDraftRequest request = new SaveTaskDraftRequest();
        request.setDeliveryLocation("5号宿舍楼");
        TaskView draft = taskService.saveDraft(REQUESTER_ID, request);

        assertThat(draft.getStatus()).isEqualTo(TaskStatus.DRAFT);
        assertThat(draft.getDeliveryLocation()).isEqualTo("5号宿舍楼");
    }

    @Test
    void deleteDraftRemovesHistoryBeforeTask() {
        Task draft = task(TaskStatus.DRAFT);
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(draft);
        when(taskMapper.deleteById(TASK_ID)).thenReturn(1);

        taskService.deleteDraft(REQUESTER_ID, TASK_ID);

        verify(taskHistoryService).deleteByTaskId(TASK_ID);
        verify(taskMapper).deleteById(TASK_ID);
    }

    @Test
    void saveDraftAllowsIncompleteFieldsAndPastDeadline() {
        when(campusVerificationMapper.selectLatestByUserId(REQUESTER_ID)).thenReturn(verifiedRecord());
        when(taskMapper.insert(any(Task.class))).thenReturn(1);
        SaveTaskDraftRequest request = new SaveTaskDraftRequest();
        request.setDeadline(LocalDateTime.now().minusDays(1));

        TaskView draft = taskService.saveDraft(REQUESTER_ID, request);

        assertThat(draft.getStatus()).isEqualTo(TaskStatus.DRAFT);
        assertThat(draft.getTitle()).isNull();
    }

    @Test
    void resendRejectsExcessivePointsRewardBeforeWriting() {
        when(campusVerificationMapper.selectLatestByUserId(REQUESTER_ID)).thenReturn(verifiedRecord());
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task(TaskStatus.DRAFT));
        CreateTaskRequest request = normalCreateRequest();
        request.setRewardAmount(11);

        assertThatThrownBy(() -> taskService.resendTask(REQUESTER_ID, TASK_ID, request))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMMON_001_VALIDATION_ERROR));
        verify(contentModerationService, org.mockito.Mockito.never())
                .assertTaskPublishAllowed(any(), any());
    }

    private CreateTaskRequest normalCreateRequest() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setCategory(TaskCategory.ERRAND);
        request.setTitle("帮取快递");
        request.setDescription("从校门口快递点取一个小包裹。");
        request.setCampus("仙林校区");
        request.setLocation("南门快递点");
        request.setDeliveryLocation("5号宿舍楼");
        request.setDeadline(LocalDateTime.now().plusDays(1));
        request.setRewardType(RewardType.POINTS);
        request.setRewardDescription("10 积分");
        request.setRewardAmount(10);
        request.setCompletionStandard("送到宿舍楼下并拍照确认");
        request.setRequiresProof(true);
        return request;
    }

    private TaskStatusTransitionRequest transitionRequest(
            TaskStatusTransitionAction action,
            String reason) {
        TaskStatusTransitionRequest request =
                new TaskStatusTransitionRequest();
        request.setAction(action);
        request.setReason(reason);
        return request;
    }

    private CampusVerification verifiedRecord() {
        CampusVerification verification = new CampusVerification();
        verification.setUserId(REQUESTER_ID);
        verification.setStatus(VerificationStatus.VERIFIED);
        return verification;
    }

    private Task task(TaskStatus status) {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setRequesterId(REQUESTER_ID);
        task.setProviderId(PROVIDER_ID);
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}
