package com.campusbridge.unit;

import com.campusbridge.enums.TaskHistoryEventType;
import com.campusbridge.entity.Task;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.TaskNotificationService;
import com.campusbridge.service.TaskHistoryService;
import com.campusbridge.service.impl.TaskTimeoutServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BE14TaskTimeoutServiceImplTest {
    private static final Long TASK_ID = 10L;
    private static final Long REQUESTER_ID = 1L;
    private static final Long PROVIDER_ID = 2L;
    private static final int SCAN_LIMIT = 100;
    private static final int CONFIRMATION_REMINDER_HOURS = 24;
    private static final int TRANSITION_GRACE_HOURS = 24;

    private static final TaskHistoryEventType EVENT_OPEN_TIMEOUT_REMINDER = TaskHistoryEventType.OPEN_TIMEOUT_REMINDER;
    private static final TaskHistoryEventType EVENT_OPEN_TIMEOUT_CANCELLED = TaskHistoryEventType.OPEN_TIMEOUT_CANCELLED;
    private static final TaskHistoryEventType EVENT_CONFIRMATION_TIMEOUT_REMINDER =
            TaskHistoryEventType.CONFIRMATION_TIMEOUT_REMINDER;
    private static final TaskHistoryEventType EVENT_CONFIRMATION_TIMEOUT_COMPLETED =
            TaskHistoryEventType.CONFIRMATION_TIMEOUT_COMPLETED;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private TaskHistoryService taskHistoryService;

    @Mock
    private TaskNotificationService notificationService;

    @Mock
    private CreditService creditService;

    private TaskTimeoutServiceImpl taskTimeoutService;

    @BeforeEach
    void setUp() {
        taskTimeoutService = new TaskTimeoutServiceImpl(
                taskMapper,
                taskHistoryService,
                notificationService,
                creditService,
                mock(PlatformTransactionManager.class),
                SCAN_LIMIT,
                CONFIRMATION_REMINDER_HOURS,
                TRANSITION_GRACE_HOURS);
    }

    @Test
    void processTimeoutsRemindsOpenTaskBeforeCancelling() {
        LocalDateTime now = LocalDateTime.now();
        Task task = task(TaskStatus.OPEN);
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task);
        stubNoTransitionCandidates();
        when(taskMapper.selectOpenTasksNeedingTimeoutReminder(
                now,
                EVENT_OPEN_TIMEOUT_REMINDER,
                SCAN_LIMIT))
                .thenReturn(List.of(task));

        int processedCount = taskTimeoutService.processTimeouts(now);

        assertThat(processedCount).isEqualTo(1);
        verify(taskHistoryService).recordHistory(
                TASK_ID,
                TaskStatus.OPEN,
                TaskStatus.OPEN,
                null,
                EVENT_OPEN_TIMEOUT_REMINDER, null);
        verify(notificationService).notifyOpenTaskTimeoutReminder(task);
    }

    @Test
    void processTimeoutsCancelsOpenTaskAfterReminderGracePeriod() {
        LocalDateTime now = LocalDateTime.now();
        Task task = task(TaskStatus.OPEN);
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task);
        stubNoReminderCandidates(now);
        when(taskMapper.selectTasksReadyForTimeoutTransition(
                TaskStatus.OPEN,
                EVENT_OPEN_TIMEOUT_REMINDER,
                now.minusHours(TRANSITION_GRACE_HOURS),
                SCAN_LIMIT))
                .thenReturn(List.of(task));
        when(taskMapper.updateStatusById(eq(TASK_ID), eq(TaskStatus.OPEN), eq(TaskStatus.CANCELLED), any()))
                .thenReturn(1);

        when(taskHistoryService.hasReminder(TASK_ID, EVENT_OPEN_TIMEOUT_REMINDER,
                task.getUpdatedAt(), now.minusHours(TRANSITION_GRACE_HOURS))).thenReturn(true);

        int processedCount = taskTimeoutService.processTimeouts(now);

        assertThat(processedCount).isEqualTo(1);
        verify(taskHistoryService).recordHistory(
                TASK_ID,
                TaskStatus.OPEN,
                TaskStatus.CANCELLED,
                null,
                EVENT_OPEN_TIMEOUT_CANCELLED, "Task timed out before a helper was accepted.");
        verify(notificationService).notifyTaskStatusChanged(
                task,
                TaskStatus.CANCELLED,
                null,
                "Task timed out before a helper was accepted.");
    }

    @Test
    void processTimeoutsAutoCompletesPendingConfirmationAfterReminderGracePeriod() {
        LocalDateTime now = LocalDateTime.now();
        Task task = task(TaskStatus.PENDING_CONFIRMATION);
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task);
        stubNoReminderCandidates(now);
        when(taskMapper.selectTasksReadyForTimeoutTransition(
                TaskStatus.OPEN,
                EVENT_OPEN_TIMEOUT_REMINDER,
                now.minusHours(TRANSITION_GRACE_HOURS),
                SCAN_LIMIT))
                .thenReturn(List.of());
        when(taskMapper.selectTasksReadyForTimeoutTransition(
                TaskStatus.PENDING_CONFIRMATION,
                EVENT_CONFIRMATION_TIMEOUT_REMINDER,
                now.minusHours(TRANSITION_GRACE_HOURS),
                SCAN_LIMIT))
                .thenReturn(List.of(task));
        when(taskMapper.updateStatusById(
                eq(TASK_ID),
                eq(TaskStatus.PENDING_CONFIRMATION),
                eq(TaskStatus.COMPLETED),
                any()))
                .thenReturn(1);

        when(taskHistoryService.hasReminder(TASK_ID, EVENT_CONFIRMATION_TIMEOUT_REMINDER,
                task.getUpdatedAt(), now.minusHours(TRANSITION_GRACE_HOURS))).thenReturn(true);

        int processedCount = taskTimeoutService.processTimeouts(now);

        assertThat(processedCount).isEqualTo(1);
        verify(creditService).awardTaskReward(task);
        verify(taskHistoryService).recordHistory(
                TASK_ID,
                TaskStatus.PENDING_CONFIRMATION,
                TaskStatus.COMPLETED,
                null,
                EVENT_CONFIRMATION_TIMEOUT_COMPLETED, "Requester did not confirm before timeout.");
        verify(notificationService).notifyTaskStatusChanged(
                task,
                TaskStatus.COMPLETED,
                null,
                "Requester did not confirm before timeout.");
    }

    private void stubNoReminderCandidates(LocalDateTime now) {
        when(taskMapper.selectOpenTasksNeedingTimeoutReminder(
                now,
                EVENT_OPEN_TIMEOUT_REMINDER,
                SCAN_LIMIT))
                .thenReturn(List.of());
        when(taskMapper.selectUnconfirmedTasksNeedingTimeoutReminder(
                now.minusHours(CONFIRMATION_REMINDER_HOURS),
                EVENT_CONFIRMATION_TIMEOUT_REMINDER,
                SCAN_LIMIT))
                .thenReturn(List.of());
    }

    private void stubNoTransitionCandidates() {
        when(taskMapper.selectTasksReadyForTimeoutTransition(
                eq(TaskStatus.OPEN),
                eq(EVENT_OPEN_TIMEOUT_REMINDER),
                any(LocalDateTime.class),
                eq(SCAN_LIMIT)))
                .thenReturn(List.of());
        when(taskMapper.selectUnconfirmedTasksNeedingTimeoutReminder(
                any(LocalDateTime.class),
                eq(EVENT_CONFIRMATION_TIMEOUT_REMINDER),
                eq(SCAN_LIMIT)))
                .thenReturn(List.of());
        when(taskMapper.selectTasksReadyForTimeoutTransition(
                eq(TaskStatus.PENDING_CONFIRMATION),
                eq(EVENT_CONFIRMATION_TIMEOUT_REMINDER),
                any(LocalDateTime.class),
                eq(SCAN_LIMIT)))
                .thenReturn(List.of());
    }

    private Task task(TaskStatus status) {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setRequesterId(REQUESTER_ID);
        task.setProviderId(PROVIDER_ID);
        task.setStatus(status);
        task.setTitle("timeout task");
        task.setDeadline(LocalDateTime.now().minusDays(1));
        task.setUpdatedAt(LocalDateTime.now().minusDays(2));
        return task;
    }
}
