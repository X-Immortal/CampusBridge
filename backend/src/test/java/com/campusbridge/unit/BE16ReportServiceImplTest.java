package com.campusbridge.unit;

import com.campusbridge.enums.TaskHistoryEventType;
import com.campusbridge.enums.CreditSourceType;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.CreateReportRequest;
import com.campusbridge.dto.request.ResolveReportRequest;
import com.campusbridge.entity.ModerationRecord;
import com.campusbridge.entity.Report;
import com.campusbridge.entity.Task;
import com.campusbridge.entity.User;
import com.campusbridge.enums.ReportStatus;
import com.campusbridge.enums.ReportTargetType;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.mapper.ModerationRecordMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.mapper.ReportMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.TaskNotificationService;
import com.campusbridge.service.TaskHistoryService;
import com.campusbridge.service.impl.ReportServiceImpl;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private TaskNotificationService notificationService;

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
        Task task = task(TaskStatus.IN_PROGRESS);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(reportMapper.insert(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId(REPORT_ID);
            return 1;
        });
        when(taskMapper.updateStatusById(eq(TASK_ID), eq(TaskStatus.IN_PROGRESS), eq(TaskStatus.DISPUTED), any()))
                .thenReturn(1);

        Report report = reportService.createReport(REPORTER_ID, taskReportRequest());

        assertThat(report.getId()).isEqualTo(REPORT_ID);
        assertThat(report.getReporterId()).isEqualTo(REPORTER_ID);
        assertThat(report.getTargetType()).isEqualTo(ReportTargetType.TASK);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
        verify(taskHistoryService).recordHistory(
                TASK_ID,
                TaskStatus.IN_PROGRESS,
                TaskStatus.DISPUTED,
                REPORTER_ID,
                TaskHistoryEventType.REPORT_CREATED, "举报 #20；原因：provider disappeared");
        verify(notificationService).notifyTaskStatusChanged(
                task,
                TaskStatus.DISPUTED,
                REPORTER_ID,
                "provider disappeared");
    }

    @ParameterizedTest
    @CsvSource(value = {"DEFAULT,-10", "-3,-3"}, nullValues = "DEFAULT")
    void resolvePunishedTaskReportWritesRecordAndPenalizesCounterparty(
            Integer requestedChange, int expectedChange) {
        Report report = taskReport(ReportStatus.PENDING);
        Task task = task(TaskStatus.DISPUTED);
        User punishedUser = new User();
        punishedUser.setId(PROVIDER_ID);

        when(reportMapper.selectById(REPORT_ID)).thenReturn(report);
        when(reportMapper.updateStatusById(REPORT_ID, ReportStatus.PUNISHED)).thenReturn(1);
        when(moderationRecordMapper.insert(any(ModerationRecord.class))).thenAnswer(invocation -> {
            ModerationRecord record = invocation.getArgument(0);
            record.setId(MODERATION_RECORD_ID);
            return 1;
        });
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(userMapper.selectById(PROVIDER_ID)).thenReturn(punishedUser);

        ResolveReportRequest request = resolveRequest(ReportStatus.PUNISHED, "confirmed no-show");
        request.setCreditChange(requestedChange);
        ModerationRecord record = reportService.resolveReport(ADMIN_ID, REPORT_ID, request);

        assertThat(record.getId()).isEqualTo(MODERATION_RECORD_ID);
        assertThat(record.getReportId()).isEqualTo(REPORT_ID);
        assertThat(record.getStatus()).isEqualTo(ReportStatus.PUNISHED);
        assertThat(record.getHandlerId()).isEqualTo(ADMIN_ID);
        verify(creditService).adjustCredit(
                PROVIDER_ID,
                expectedChange,
                "REPORT_PUNISHED: confirmed no-show",
                CreditSourceType.REPORT,
                REPORT_ID);
    }

    @Test
    void resolveReportRejectsAlreadyResolvedReport() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(taskReport(ReportStatus.RESOLVED));

        assertThatThrownBy(() -> reportService.resolveReport(
                ADMIN_ID,
                REPORT_ID,
                resolveRequest(ReportStatus.PUNISHED, "duplicate handling")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMMON_003_CONFLICT));
    }

    @Test
    void resolveRaceDoesNotWriteModerationOrRepeatCreditChange() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(taskReport(ReportStatus.PENDING));
        when(reportMapper.updateStatusById(REPORT_ID, ReportStatus.PUNISHED)).thenReturn(0);
        assertThatThrownBy(() -> reportService.resolveReport(
                ADMIN_ID, REPORT_ID, resolveRequest(ReportStatus.PUNISHED, "race")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMMON_003_CONFLICT));
        verifyNoInteractions(moderationRecordMapper, creditService);
    }

    @Test
    void cannotReportMessageFromAnotherUsersConversation() {
        CreateReportRequest request = new CreateReportRequest();
        request.setTargetType(ReportTargetType.MESSAGE);
        request.setTargetId(123L);
        request.setReason("Spam");
        assertThatThrownBy(() -> reportService.createReport(REPORTER_ID, request))
                .isInstanceOf(BusinessException.class);
        verify(messageMapper).selectOwnedById(123L, REPORTER_ID);
        verify(reportMapper, never()).insert(any(Report.class));
    }

    @Test
    void resolveReportRejectsNonTerminalTargetStatusBeforeWriting() {
        for (ReportStatus status : java.util.List.of(ReportStatus.PENDING, ReportStatus.PROCESSING)) {
            assertThatThrownBy(() -> reportService.resolveReport(
                    ADMIN_ID, REPORT_ID, resolveRequest(status, "Not a final result")))
                    .isInstanceOfSatisfying(BusinessException.class, error ->
                            assertThat(error.getErrorCode()).isEqualTo(ErrorCode.COMMON_001_VALIDATION_ERROR));
        }
        verifyNoInteractions(reportMapper, moderationRecordMapper, creditService);
    }

    private CreateReportRequest taskReportRequest() {
        CreateReportRequest request = new CreateReportRequest();
        request.setTargetType(ReportTargetType.TASK);
        request.setTargetId(TASK_ID);
        request.setReason("provider disappeared");
        request.setDescription("No response after accepting the task.");
        return request;
    }

    private ResolveReportRequest resolveRequest(
            ReportStatus status,
            String decision) {
        ResolveReportRequest request = new ResolveReportRequest();
        request.setStatus(status);
        request.setDecision(decision);
        return request;
    }

    private Report taskReport(ReportStatus status) {
        Report report = new Report();
        report.setId(REPORT_ID);
        report.setReporterId(REPORTER_ID);
        report.setTargetType(ReportTargetType.TASK);
        report.setTargetId(TASK_ID);
        report.setReason("provider disappeared");
        report.setDescription("No response after accepting the task.");
        report.setStatus(status);
        report.setCreatedAt(LocalDateTime.now());
        return report;
    }

    private Task task(TaskStatus status) {
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
