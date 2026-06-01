package com.campusbridge.unit;

import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.TaskHistoryService;
import com.campusbridge.service.impl.TaskTimeoutServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private static final String NOTE_OPEN_TIMEOUT_REMINDER = "TIMEOUT_REMINDER_OPEN";
    private static final String NOTE_OPEN_TIMEOUT_CANCELLED = "TIMEOUT_CANCELLED_OPEN";
    private static final String NOTE_CONFIRMATION_TIMEOUT_REMINDER =
            "TIMEOUT_REMINDER_PENDING_CONFIRMATION";
    private static final String NOTE_CONFIRMATION_TIMEOUT_COMPLETED =
            "TIMEOUT_AUTO_COMPLETED_PENDING_CONFIRMATION";

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private TaskHistoryService taskHistoryService;

    @Mock
    private NotificationService notificationService;

    private TaskTimeoutServiceImpl taskTimeoutService;

    @BeforeEach
    void setUp() {
        taskTimeoutService = new TaskTimeoutServiceImpl(
                taskMapper,
                taskHistoryService,
                notificationService,
                SCAN_LIMIT,
                CONFIRMATION_REMINDER_HOURS,
                TRANSITION_GRACE_HOURS);
    }

    @Test
    void processTimeoutsRemindsOpenTaskBeforeCancelling() {
        LocalDateTime now = LocalDateTime.now();
        Task task = task("OPEN");
        stubNoTransitionCandidates();
        when(taskMapper.selectTasksNeedingTimeoutReminder(
                "OPEN",
                now,
                NOTE_OPEN_TIMEOUT_REMINDER,
                SCAN_LIMIT))
                .thenReturn(List.of(task));

        int processedCount = taskTimeoutService.processTimeouts(now);

        assertThat(processedCount).isEqualTo(1);
        verify(taskHistoryService).recordHistory(
                TASK_ID,
                "OPEN",
                "OPEN",
                null,
                NOTE_OPEN_TIMEOUT_REMINDER);
        verify(notificationService).notifyTaskTimeoutReminder(
                task,
                REQUESTER_ID,
                null,
                NOTE_OPEN_TIMEOUT_REMINDER);
    }

    @Test
    void processTimeoutsCancelsOpenTaskAfterReminderGracePeriod() {
        LocalDateTime now = LocalDateTime.now();
        Task task = task("OPEN");
        stubNoReminderCandidates(now);
        when(taskMapper.selectTasksReadyForTimeoutTransition(
                "OPEN",
                NOTE_OPEN_TIMEOUT_REMINDER,
                now.minusHours(TRANSITION_GRACE_HOURS),
                SCAN_LIMIT))
                .thenReturn(List.of(task));
        when(taskMapper.updateStatusById(eq(TASK_ID), eq("OPEN"), eq("CANCELLED"), any()))
                .thenReturn(1);

        int processedCount = taskTimeoutService.processTimeouts(now);

        assertThat(processedCount).isEqualTo(1);
        verify(taskHistoryService).recordHistory(
                TASK_ID,
                "OPEN",
                "CANCELLED",
                null,
                NOTE_OPEN_TIMEOUT_CANCELLED);
        verify(notificationService).notifyTaskStatusChanged(
                task,
                "OPEN",
                "CANCELLED",
                null,
                "Task timed out before a helper was accepted.");
    }

    @Test
    void processTimeoutsAutoCompletesPendingConfirmationAfterReminderGracePeriod() {
        LocalDateTime now = LocalDateTime.now();
        Task task = task("PENDING_CONFIRMATION");
        stubNoReminderCandidates(now);
        when(taskMapper.selectTasksReadyForTimeoutTransition(
                "OPEN",
                NOTE_OPEN_TIMEOUT_REMINDER,
                now.minusHours(TRANSITION_GRACE_HOURS),
                SCAN_LIMIT))
                .thenReturn(List.of());
        when(taskMapper.selectTasksReadyForTimeoutTransition(
                "PENDING_CONFIRMATION",
                NOTE_CONFIRMATION_TIMEOUT_REMINDER,
                now.minusHours(TRANSITION_GRACE_HOURS),
                SCAN_LIMIT))
                .thenReturn(List.of(task));
        when(taskMapper.updateStatusById(
                eq(TASK_ID),
                eq("PENDING_CONFIRMATION"),
                eq("COMPLETED"),
                any()))
                .thenReturn(1);

        int processedCount = taskTimeoutService.processTimeouts(now);

        assertThat(processedCount).isEqualTo(1);
        verify(taskHistoryService).recordHistory(
                TASK_ID,
                "PENDING_CONFIRMATION",
                "COMPLETED",
                null,
                NOTE_CONFIRMATION_TIMEOUT_COMPLETED);
        verify(notificationService).notifyTaskStatusChanged(
                task,
                "PENDING_CONFIRMATION",
                "COMPLETED",
                null,
                "Requester did not confirm before timeout.");
    }

    private void stubNoReminderCandidates(LocalDateTime now) {
        when(taskMapper.selectTasksNeedingTimeoutReminder(
                "OPEN",
                now,
                NOTE_OPEN_TIMEOUT_REMINDER,
                SCAN_LIMIT))
                .thenReturn(List.of());
        when(taskMapper.selectTasksNeedingTimeoutReminder(
                "PENDING_CONFIRMATION",
                now.minusHours(CONFIRMATION_REMINDER_HOURS),
                NOTE_CONFIRMATION_TIMEOUT_REMINDER,
                SCAN_LIMIT))
                .thenReturn(List.of());
    }

    private void stubNoTransitionCandidates() {
        when(taskMapper.selectTasksReadyForTimeoutTransition(
                eq("OPEN"),
                eq(NOTE_OPEN_TIMEOUT_REMINDER),
                any(LocalDateTime.class),
                eq(SCAN_LIMIT)))
                .thenReturn(List.of());
        when(taskMapper.selectTasksNeedingTimeoutReminder(
                eq("PENDING_CONFIRMATION"),
                any(LocalDateTime.class),
                eq(NOTE_CONFIRMATION_TIMEOUT_REMINDER),
                eq(SCAN_LIMIT)))
                .thenReturn(List.of());
        when(taskMapper.selectTasksReadyForTimeoutTransition(
                eq("PENDING_CONFIRMATION"),
                eq(NOTE_CONFIRMATION_TIMEOUT_REMINDER),
                any(LocalDateTime.class),
                eq(SCAN_LIMIT)))
                .thenReturn(List.of());
    }

    private Task task(String status) {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setRequesterId(REQUESTER_ID);
        task.setProviderId(PROVIDER_ID);
        task.setStatus(status);
        task.setTitle("timeout task");
        task.setUpdatedAt(LocalDateTime.now().minusDays(2));
        return task;
    }
}
