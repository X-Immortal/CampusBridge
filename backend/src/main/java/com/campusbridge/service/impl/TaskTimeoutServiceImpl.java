package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.TaskHistoryService;
import com.campusbridge.service.TaskTimeoutService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Transactional
@Service
public class TaskTimeoutServiceImpl implements TaskTimeoutService {
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_PENDING_CONFIRMATION = "PENDING_CONFIRMATION";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private static final String NOTE_OPEN_TIMEOUT_REMINDER = "TIMEOUT_REMINDER_OPEN";
    private static final String NOTE_OPEN_TIMEOUT_CANCELLED = "TIMEOUT_CANCELLED_OPEN";
    private static final String NOTE_CONFIRMATION_TIMEOUT_REMINDER =
            "TIMEOUT_REMINDER_PENDING_CONFIRMATION";
    private static final String NOTE_CONFIRMATION_TIMEOUT_COMPLETED =
            "TIMEOUT_AUTO_COMPLETED_PENDING_CONFIRMATION";

    private final TaskMapper taskMapper;
    private final TaskHistoryService taskHistoryService;
    private final NotificationService notificationService;
    private final int scanLimit;
    private final int confirmationReminderHours;
    private final int transitionGraceHours;

    public TaskTimeoutServiceImpl(
            TaskMapper taskMapper,
            TaskHistoryService taskHistoryService,
            NotificationService notificationService,
            @Value("${task.timeout.scan-limit:100}") int scanLimit,
            @Value("${task.timeout.confirmation-reminder-hours:24}") int confirmationReminderHours,
            @Value("${task.timeout.transition-grace-hours:24}") int transitionGraceHours) {
        this.taskMapper = taskMapper;
        this.taskHistoryService = taskHistoryService;
        this.notificationService = notificationService;
        this.scanLimit = scanLimit;
        this.confirmationReminderHours = confirmationReminderHours;
        this.transitionGraceHours = transitionGraceHours;
    }

    @Scheduled(
            fixedDelayString = "${task.timeout.scan-fixed-delay-ms:600000}",
            initialDelayString = "${task.timeout.scan-initial-delay-ms:60000}")
    public void scheduledProcessTimeouts() {
        processTimeouts(LocalDateTime.now());
    }

    @Override
    public int processTimeouts(LocalDateTime now) {
        if (now == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "now must not be null");
        }
        int processedCount = 0;
        processedCount += remindOpenTasks(now);
        processedCount += cancelRemindedOpenTasks(now);
        processedCount += remindPendingConfirmationTasks(now);
        processedCount += completeRemindedPendingConfirmationTasks(now);
        return processedCount;
    }

    private int remindOpenTasks(LocalDateTime now) {
        List<Task> tasks = taskMapper.selectTasksNeedingTimeoutReminder(
                STATUS_OPEN,
                now,
                NOTE_OPEN_TIMEOUT_REMINDER,
                scanLimit);
        for (Task task : tasks) {
            taskHistoryService.recordHistory(
                    task.getId(),
                    STATUS_OPEN,
                    STATUS_OPEN,
                    null,
                    NOTE_OPEN_TIMEOUT_REMINDER);
            notificationService.notifyTaskTimeoutReminder(
                    task,
                    task.getRequesterId(),
                    null,
                    NOTE_OPEN_TIMEOUT_REMINDER);
        }
        return tasks.size();
    }

    private int cancelRemindedOpenTasks(LocalDateTime now) {
        LocalDateTime reminderCreatedBefore = now.minusHours(transitionGraceHours);
        List<Task> tasks = taskMapper.selectTasksReadyForTimeoutTransition(
                STATUS_OPEN,
                NOTE_OPEN_TIMEOUT_REMINDER,
                reminderCreatedBefore,
                scanLimit);
        int processedCount = 0;
        for (Task task : tasks) {
            LocalDateTime updatedAt = LocalDateTime.now();
            int affectedRows = taskMapper.updateStatusById(
                    task.getId(),
                    STATUS_OPEN,
                    STATUS_CANCELLED,
                    updatedAt);
            if (affectedRows == 1) {
                taskHistoryService.recordHistory(
                        task.getId(),
                        STATUS_OPEN,
                        STATUS_CANCELLED,
                        null,
                        NOTE_OPEN_TIMEOUT_CANCELLED);
                task.setStatus(STATUS_CANCELLED);
                task.setUpdatedAt(updatedAt);
                notificationService.notifyTaskStatusChanged(
                        task,
                        STATUS_OPEN,
                        STATUS_CANCELLED,
                        null,
                        "Task timed out before a helper was accepted.");
                processedCount++;
            }
        }
        return processedCount;
    }

    private int remindPendingConfirmationTasks(LocalDateTime now) {
        LocalDateTime referenceTime = now.minusHours(confirmationReminderHours);
        List<Task> tasks = taskMapper.selectTasksNeedingTimeoutReminder(
                STATUS_PENDING_CONFIRMATION,
                referenceTime,
                NOTE_CONFIRMATION_TIMEOUT_REMINDER,
                scanLimit);
        for (Task task : tasks) {
            taskHistoryService.recordHistory(
                    task.getId(),
                    STATUS_PENDING_CONFIRMATION,
                    STATUS_PENDING_CONFIRMATION,
                    null,
                    NOTE_CONFIRMATION_TIMEOUT_REMINDER);
            notificationService.notifyTaskTimeoutReminder(
                    task,
                    task.getRequesterId(),
                    null,
                    NOTE_CONFIRMATION_TIMEOUT_REMINDER);
        }
        return tasks.size();
    }

    private int completeRemindedPendingConfirmationTasks(LocalDateTime now) {
        LocalDateTime reminderCreatedBefore = now.minusHours(transitionGraceHours);
        List<Task> tasks = taskMapper.selectTasksReadyForTimeoutTransition(
                STATUS_PENDING_CONFIRMATION,
                NOTE_CONFIRMATION_TIMEOUT_REMINDER,
                reminderCreatedBefore,
                scanLimit);
        int processedCount = 0;
        for (Task task : tasks) {
            LocalDateTime updatedAt = LocalDateTime.now();
            int affectedRows = taskMapper.updateStatusById(
                    task.getId(),
                    STATUS_PENDING_CONFIRMATION,
                    STATUS_COMPLETED,
                    updatedAt);
            if (affectedRows == 1) {
                taskHistoryService.recordHistory(
                        task.getId(),
                        STATUS_PENDING_CONFIRMATION,
                        STATUS_COMPLETED,
                        null,
                        NOTE_CONFIRMATION_TIMEOUT_COMPLETED);
                task.setStatus(STATUS_COMPLETED);
                task.setUpdatedAt(updatedAt);
                notificationService.notifyTaskStatusChanged(
                        task,
                        STATUS_PENDING_CONFIRMATION,
                        STATUS_COMPLETED,
                        null,
                        "Requester did not confirm before timeout.");
                processedCount++;
            }
        }
        return processedCount;
    }
}
