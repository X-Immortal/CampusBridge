package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.pojo.CampusVerification;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.ContentModerationService;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.TaskHistoryService;
import com.campusbridge.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private NotificationService notificationService;

    private TaskServiceImpl taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl(
                taskMapper,
                campusVerificationMapper,
                taskHistoryService,
                contentModerationService,
                notificationService);
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

        Task created = taskService.createTask(REQUESTER_ID, normalCreateRequest());

        assertThat(created.getId()).isEqualTo(TASK_ID);
        assertThat(created.getRequesterId()).isEqualTo(REQUESTER_ID);
        assertThat(created.getStatus()).isEqualTo("OPEN");
        assertThat(created.getOnlyInAppContact()).isEqualTo(1);
        assertThat(created.getAllowOfflineContact()).isEqualTo(0);
        verify(contentModerationService)
                .assertTaskPublishAllowed(eq(REQUESTER_ID), any(RequestDtos.CreateTaskRequest.class));
        verify(taskHistoryService)
                .recordHistory(TASK_ID, null, "OPEN", REQUESTER_ID, "TASK_CREATED");
    }

    @Test
    void createTaskCreatesPendingReviewTaskForSensitiveCategory() {
        RequestDtos.CreateTaskRequest request = normalCreateRequest();
        request.setCategory(ApiEnums.TaskCategory.ATTENDANCE_ASSIST);
        request.setRiskDisclosureConfirmed(true);
        request.setSensitiveReason("课程临时签到协助，需要管理员审核。");

        when(campusVerificationMapper.selectLatestByUserId(REQUESTER_ID))
                .thenReturn(verifiedRecord());
        when(taskMapper.insert(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(TASK_ID);
            return 1;
        });

        Task created = taskService.createTask(REQUESTER_ID, request);

        assertThat(created.getStatus()).isEqualTo("PENDING_REVIEW");
        verify(taskHistoryService)
                .recordHistory(TASK_ID, null, "PENDING_REVIEW", REQUESTER_ID, "TASK_CREATED");
    }

    @Test
    void createTaskRejectsMissingRequiredField() {
        RequestDtos.CreateTaskRequest request = normalCreateRequest();
        request.setTitle(" ");

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
        Task acceptedTask = task("ACCEPTED");
        acceptedTask.setProviderId(PROVIDER_ID);
        when(taskMapper.selectById(TASK_ID)).thenReturn(acceptedTask);
        when(taskMapper.updateStatusById(
                eq(TASK_ID),
                eq("ACCEPTED"),
                eq("IN_PROGRESS"),
                any(LocalDateTime.class))).thenReturn(1);

        Task transitioned = taskService.transitionTaskStatus(
                PROVIDER_ID,
                TASK_ID,
                transitionRequest(ApiEnums.TaskStatusTransitionAction.START, null));

        assertThat(transitioned.getStatus()).isEqualTo("IN_PROGRESS");
        verify(taskHistoryService).recordHistory(
                TASK_ID,
                "ACCEPTED",
                "IN_PROGRESS",
                PROVIDER_ID,
                "ACTION=START");
    }

    @Test
    void transitionTaskStatusRejectsIllegalTransition() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("COMPLETED"));

        assertThatThrownBy(() -> taskService.transitionTaskStatus(
                REQUESTER_ID,
                TASK_ID,
                transitionRequest(ApiEnums.TaskStatusTransitionAction.CANCEL, "取消测试")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION));
    }

    @Test
    void transitionTaskStatusRequiresReasonForDispute() {
        Task inProgressTask = task("IN_PROGRESS");
        inProgressTask.setProviderId(PROVIDER_ID);
        when(taskMapper.selectById(TASK_ID)).thenReturn(inProgressTask);

        assertThatThrownBy(() -> taskService.transitionTaskStatus(
                PROVIDER_ID,
                TASK_ID,
                transitionRequest(ApiEnums.TaskStatusTransitionAction.DISPUTE, " ")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.COMMON_001_VALIDATION_ERROR));
    }

    private RequestDtos.CreateTaskRequest normalCreateRequest() {
        RequestDtos.CreateTaskRequest request = new RequestDtos.CreateTaskRequest();
        request.setCategory(ApiEnums.TaskCategory.ERRAND);
        request.setTitle("帮取快递");
        request.setDescription("从校门口快递点取一个小包裹。");
        request.setCampus("仙林校区");
        request.setLocation("南门快递点");
        request.setDeadline(LocalDateTime.now().plusDays(1));
        request.setRewardType(ApiEnums.RewardType.POINTS);
        request.setRewardDescription("20 积分");
        request.setCompletionStandard("送到宿舍楼下并拍照确认");
        request.setRequiresProof(true);
        return request;
    }

    private RequestDtos.TaskStatusTransitionRequest transitionRequest(
            ApiEnums.TaskStatusTransitionAction action,
            String reason) {
        RequestDtos.TaskStatusTransitionRequest request =
                new RequestDtos.TaskStatusTransitionRequest();
        request.setAction(action);
        request.setReason(reason);
        return request;
    }

    private CampusVerification verifiedRecord() {
        CampusVerification verification = new CampusVerification();
        verification.setUserId(REQUESTER_ID);
        verification.setStatus("VERIFIED");
        return verification;
    }

    private Task task(String status) {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setRequesterId(REQUESTER_ID);
        task.setProviderId(PROVIDER_ID);
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}
