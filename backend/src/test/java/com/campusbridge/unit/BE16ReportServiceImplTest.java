package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.mapper.ModerationRecordMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.mapper.ReportMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.pojo.ModerationRecord;
import com.campusbridge.pojo.Report;
import com.campusbridge.pojo.Task;
import com.campusbridge.pojo.User;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.TaskHistoryService;
import com.campusbridge.service.impl.ReportServiceImpl;
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
class BE16ReportServiceImplTest {
    private static final Long REPORTER_ID = 1L;
    private static final Long PROVIDER_ID = 2L;
    private static final Long ADMIN_ID = 99L;
    private static final Long TASK_ID = 10L;
    private static final Long REPORT_ID = 20L;
    private static final Long MODERATION_RECORD_ID = 30L;

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private ModerationRecordMapper moderationRecordMapper;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PostMapper postMapper;

    @Mock
    private ReplyMapper replyMapper;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private TaskHistoryService taskHistoryService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CreditService creditService;

    private ReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(
                reportMapper,
                moderationRecordMapper,
                taskMapper,
                userMapper,
                postMapper,
                replyMapper,
                messageMapper,
                taskHistoryService,
                notificationService,
                creditService);
    }

    @Test
    void createTaskReportTurnsParticipantTaskDisputed() {
        Task task = task("IN_PROGRESS");
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(reportMapper.insert(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId(REPORT_ID);
            return 1;
        });
        when(taskMapper.updateStatusById(eq(TASK_ID), eq("IN_PROGRESS"), eq("DISPUTED"), any()))
                .thenReturn(1);

        Report report = reportService.createReport(REPORTER_ID, taskReportRequest());

        assertThat(report.getId()).isEqualTo(REPORT_ID);
        assertThat(report.getReporterId()).isEqualTo(REPORTER_ID);
        assertThat(report.getTargetType()).isEqualTo("TASK");
        assertThat(report.getStatus()).isEqualTo("PENDING");
        verify(taskHistoryService).recordHistory(
                TASK_ID,
                "IN_PROGRESS",
                "DISPUTED",
                REPORTER_ID,
                "REPORT_CREATED; REPORT_ID=20; REASON=provider disappeared");
        verify(notificationService).notifyTaskStatusChanged(
                task,
                "IN_PROGRESS",
                "DISPUTED",
                REPORTER_ID,
                "provider disappeared");
    }

    @Test
    void resolvePunishedTaskReportWritesRecordAndPenalizesCounterparty() {
        Report report = taskReport("PENDING");
        Task task = task("DISPUTED");
        User punishedUser = new User();
        punishedUser.setId(PROVIDER_ID);

        when(reportMapper.selectById(REPORT_ID)).thenReturn(report);
        when(reportMapper.updateStatusById(REPORT_ID, "PUNISHED")).thenReturn(1);
        when(moderationRecordMapper.insert(any(ModerationRecord.class))).thenAnswer(invocation -> {
            ModerationRecord record = invocation.getArgument(0);
            record.setId(MODERATION_RECORD_ID);
            return 1;
        });
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(userMapper.selectById(PROVIDER_ID)).thenReturn(punishedUser);

        ModerationRecord record = reportService.resolveReport(
                ADMIN_ID,
                REPORT_ID,
                resolveRequest(ApiEnums.ModerationStatus.PUNISHED, "confirmed no-show"));

        assertThat(record.getId()).isEqualTo(MODERATION_RECORD_ID);
        assertThat(record.getReportId()).isEqualTo(REPORT_ID);
        assertThat(record.getStatus()).isEqualTo("PUNISHED");
        assertThat(record.getHandlerId()).isEqualTo(ADMIN_ID);
        verify(creditService).adjustCredit(
                PROVIDER_ID,
                -10,
                "REPORT_PUNISHED: confirmed no-show",
                "REPORT",
                REPORT_ID);
    }

    @Test
    void resolveReportRejectsAlreadyResolvedReport() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(taskReport("RESOLVED"));

        assertThatThrownBy(() -> reportService.resolveReport(
                ADMIN_ID,
                REPORT_ID,
                resolveRequest(ApiEnums.ModerationStatus.PUNISHED, "duplicate handling")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMMON_003_CONFLICT));
    }

    private RequestDtos.CreateReportRequest taskReportRequest() {
        RequestDtos.CreateReportRequest request = new RequestDtos.CreateReportRequest();
        request.setTargetType(ApiEnums.ReportTargetType.TASK);
        request.setTargetId(TASK_ID);
        request.setReason("provider disappeared");
        request.setDescription("No response after accepting the task.");
        return request;
    }

    private RequestDtos.ResolveReportRequest resolveRequest(
            ApiEnums.ModerationStatus status,
            String decision) {
        RequestDtos.ResolveReportRequest request = new RequestDtos.ResolveReportRequest();
        request.setStatus(status);
        request.setDecision(decision);
        return request;
    }

    private Report taskReport(String status) {
        Report report = new Report();
        report.setId(REPORT_ID);
        report.setReporterId(REPORTER_ID);
        report.setTargetType("TASK");
        report.setTargetId(TASK_ID);
        report.setReason("provider disappeared");
        report.setDescription("No response after accepting the task.");
        report.setStatus(status);
        report.setCreatedAt(LocalDateTime.now());
        return report;
    }

    private Task task(String status) {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setRequesterId(REPORTER_ID);
        task.setProviderId(PROVIDER_ID);
        task.setStatus(status);
        task.setTitle("reported task");
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}
