package com.campusbridge.unit;

import com.campusbridge.enums.TaskHistoryEventType;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.CreateTaskApplicationRequest;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.entity.Task;
import com.campusbridge.entity.TaskApplication;
import com.campusbridge.enums.ApplicationStatus;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.mapper.TaskApplicationMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.mapper.UserCreditMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.mapper.UserProfileMapper;
import com.campusbridge.service.TaskNotificationService;
import com.campusbridge.service.TaskHistoryService;
import com.campusbridge.service.impl.TaskApplicationServiceImpl;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BE07TaskApplicationServiceImplTest {
    private static final Long APPLICANT_ID = 10L;
    private static final Long REQUESTER_ID = 20L;
    private static final Long TASK_ID = 100L;
    private static final Long APPLICATION_ID = 200L;

    @Mock
    private TaskApplicationMapper taskApplicationMapper;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private UserCreditMapper userCreditMapper;

    @Mock
    private CampusVerificationMapper campusVerificationMapper;

    @Mock
    private TaskHistoryService taskHistoryService;

    @Mock
    private TaskNotificationService notificationService;

    private TaskApplicationServiceImpl applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new TaskApplicationServiceImpl(
                taskApplicationMapper, taskMapper, userMapper, userProfileMapper,
                userCreditMapper, campusVerificationMapper, taskHistoryService, notificationService);
    }

    // ---- createApplication ----

    @Test
    void createApplicationSucceedsForOpenTask() {
        Task task = task(TASK_ID, REQUESTER_ID, "OPEN");
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task);
        when(taskApplicationMapper.selectByTaskIdAndApplicantId(TASK_ID, APPLICANT_ID)).thenReturn(null);
        when(taskApplicationMapper.insert(any(TaskApplication.class))).thenReturn(1);

        TaskApplication result = applicationService.createApplication(
                APPLICANT_ID, TASK_ID, appRequest("我擅长跑腿"));

        assertThat(result.getTaskId()).isEqualTo(TASK_ID);
        assertThat(result.getApplicantId()).isEqualTo(APPLICANT_ID);
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        verify(notificationService)
                .notifyTaskApplicationCreated(any(Task.class), any(TaskApplication.class), eq(APPLICANT_ID));
    }

    @Test
    void createApplicationRejectsApplicationToOwnTask() {
        Task task = task(TASK_ID, APPLICANT_ID, "OPEN");
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> applicationService.createApplication(
                APPLICANT_ID, TASK_ID, appRequest("message")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_003_PERMISSION_DENIED);
    }

    @Test
    void createApplicationRejectsNonOpenTask() {
        Task task = task(TASK_ID, REQUESTER_ID, "IN_PROGRESS");
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> applicationService.createApplication(
                APPLICANT_ID, TASK_ID, appRequest("message")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
    }

    @Test
    void createApplicationRejectsDuplicateApplication() {
        Task task = task(TASK_ID, REQUESTER_ID, "OPEN");
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task);
        when(taskApplicationMapper.selectByTaskIdAndApplicantId(TASK_ID, APPLICANT_ID))
                .thenReturn(new TaskApplication());

        assertThatThrownBy(() -> applicationService.createApplication(
                APPLICANT_ID, TASK_ID, appRequest("message")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_007_ALREADY_APPLIED);
    }

    @Test
    void createApplicationReactivatesWithdrawnApplication() {
        Task task = task(TASK_ID, REQUESTER_ID, "OPEN");
        TaskApplication application = pendingApplication(APPLICATION_ID, TASK_ID, APPLICANT_ID);
        application.setStatus(ApplicationStatus.WITHDRAWN);
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task);
        when(taskApplicationMapper.selectByTaskIdAndApplicantId(TASK_ID, APPLICANT_ID))
                .thenReturn(application);
        when(taskApplicationMapper.updateById(application)).thenReturn(1);

        TaskApplication result = applicationService.createApplication(
                APPLICANT_ID, TASK_ID, appRequest("重新申请"));

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(result.getMessage()).isEqualTo("重新申请");
        assertThat(result.getHandledAt()).isNull();
        verify(notificationService)
                .notifyTaskApplicationCreated(task, application, APPLICANT_ID);
    }

    @Test
    void createApplicationRejectsNullTaskId() {
        assertThatThrownBy(() -> applicationService.createApplication(
                APPLICANT_ID, null, appRequest("message")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_001_VALIDATION_ERROR);
    }

    @Test
    void createApplicationRejectsTaskNotFound() {
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(null);

        assertThatThrownBy(() -> applicationService.createApplication(
                APPLICANT_ID, TASK_ID, appRequest("message")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_001_NOT_FOUND);
    }

    // ---- acceptApplication ----

    @Test
    void acceptApplicationUpdatesTaskAndRejectsOthers() {
        Task task = task(TASK_ID, REQUESTER_ID, "OPEN");
        TaskApplication application = pendingApplication(APPLICATION_ID, TASK_ID, APPLICANT_ID);
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task);
        when(taskApplicationMapper.selectById(APPLICATION_ID)).thenReturn(application);
        when(taskMapper.acceptProviderById(
                eq(TASK_ID), eq(REQUESTER_ID), eq(APPLICANT_ID), any())).thenReturn(1);
        when(taskApplicationMapper.updateStatusById(
                eq(APPLICATION_ID), eq(ApplicationStatus.PENDING), eq(ApplicationStatus.ACCEPTED), any())).thenReturn(1);

        TaskApplication result =
                applicationService.acceptApplication(REQUESTER_ID, TASK_ID, APPLICATION_ID);

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        verify(taskApplicationMapper)
                .rejectOtherPendingByTaskId(eq(TASK_ID), eq(APPLICATION_ID), any());
        verify(taskHistoryService)
                .recordHistory(TASK_ID, TaskStatus.OPEN, TaskStatus.ACCEPTED, REQUESTER_ID, TaskHistoryEventType.APPLICATION_ACCEPTED, null);
        verify(notificationService)
                .notifyTaskApplicationAccepted(any(Task.class), any(TaskApplication.class), eq(REQUESTER_ID));
    }

    @Test
    void acceptApplicationRejectsNonRequester() {
        Task task = task(TASK_ID, REQUESTER_ID, "OPEN");
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> applicationService.acceptApplication(999L, TASK_ID, APPLICATION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_003_PERMISSION_DENIED);
    }

    @Test
    void acceptApplicationRejectsApplicationNotFound() {
        Task task = task(TASK_ID, REQUESTER_ID, "OPEN");
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task);
        when(taskApplicationMapper.selectById(APPLICATION_ID)).thenReturn(null);

        assertThatThrownBy(() -> applicationService.acceptApplication(REQUESTER_ID, TASK_ID, APPLICATION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_004_APPLICATION_NOT_FOUND);
    }

    // ---- applicant self-service ----

    @Test
    void listMyApplicationsReturnsApplicationsWithTasks() {
        TaskApplication application = pendingApplication(APPLICATION_ID, TASK_ID, APPLICANT_ID);
        Task task = task(TASK_ID, REQUESTER_ID, "OPEN");
        when(taskApplicationMapper.selectByApplicantId(APPLICANT_ID))
                .thenReturn(List.of(application));
        TaskView taskView = TaskView.from(task);
        taskView.setRequesterName("Requester");
        when(taskMapper.selectDetailById(TASK_ID)).thenReturn(taskView);

        var result = applicationService.listMyApplications(APPLICANT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getApplication()).isSameAs(application);
        assertThat(result.get(0).getTask()).isSameAs(taskView);
        assertThat(result.get(0).getTask().getRequesterName()).isEqualTo("Requester");
    }

    @Test
    void withdrawApplicationChangesPendingStatus() {
        TaskApplication application = pendingApplication(APPLICATION_ID, TASK_ID, APPLICANT_ID);
        Task task = task(TASK_ID, REQUESTER_ID, "OPEN");
        when(taskApplicationMapper.selectById(APPLICATION_ID)).thenReturn(application);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskApplicationMapper.updateStatusById(
                eq(APPLICATION_ID), eq(ApplicationStatus.PENDING), eq(ApplicationStatus.WITHDRAWN), any()))
                .thenReturn(1);

        TaskApplication result =
                applicationService.withdrawApplication(APPLICANT_ID, APPLICATION_ID);

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        assertThat(result.getHandledAt()).isNotNull();
        verify(notificationService)
                .notifyTaskApplicationWithdrawn(task, application, APPLICANT_ID);
    }

    @Test
    void withdrawApplicationRejectsDifferentApplicant() {
        TaskApplication application = pendingApplication(APPLICATION_ID, TASK_ID, APPLICANT_ID);
        when(taskApplicationMapper.selectById(APPLICATION_ID)).thenReturn(application);

        assertThatThrownBy(() -> applicationService.withdrawApplication(999L, APPLICATION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_003_PERMISSION_DENIED);
    }

    @Test
    void withdrawApplicationRejectsAcceptedApplication() {
        TaskApplication application = pendingApplication(APPLICATION_ID, TASK_ID, APPLICANT_ID);
        application.setStatus(ApplicationStatus.ACCEPTED);
        when(taskApplicationMapper.selectById(APPLICATION_ID)).thenReturn(application);

        assertThatThrownBy(() -> applicationService.withdrawApplication(APPLICANT_ID, APPLICATION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_003_CONFLICT);
    }

    // ---- helpers ----

    private CreateTaskApplicationRequest appRequest(String message) {
        CreateTaskApplicationRequest request = new CreateTaskApplicationRequest();
        request.setMessage(message);
        return request;
    }

    private Task task(Long id, Long requesterId, String status) {
        Task task = new Task();
        task.setId(id);
        task.setRequesterId(requesterId);
        task.setStatus(TaskStatus.valueOf(status));
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    private TaskApplication pendingApplication(Long id, Long taskId, Long applicantId) {
        TaskApplication application = new TaskApplication();
        application.setId(id);
        application.setTaskId(taskId);
        application.setApplicantId(applicantId);
        application.setMessage("我能帮忙");
        application.setStatus(ApplicationStatus.PENDING);
        application.setCreatedAt(LocalDateTime.now());
        return application;
    }
}
