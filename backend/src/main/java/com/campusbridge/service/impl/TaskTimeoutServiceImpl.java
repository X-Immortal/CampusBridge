package com.campusbridge.service.impl;

import com.campusbridge.entity.Task;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.enums.TaskHistoryEventType;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.TaskNotificationService;
import com.campusbridge.service.TaskHistoryService;
import com.campusbridge.service.TaskTimeoutService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskTimeoutServiceImpl implements TaskTimeoutService {
    private final TaskMapper taskMapper;
    private final TaskHistoryService taskHistoryService;
    private final TaskNotificationService notificationService;
    private final CreditService creditService;
    private final TransactionTemplate transactions;
    private final int scanLimit;
    private final int confirmationReminderHours;
    private final int transitionGraceHours;

    public TaskTimeoutServiceImpl(
            TaskMapper taskMapper,
            TaskHistoryService taskHistoryService,
            TaskNotificationService notificationService,
            CreditService creditService,
            PlatformTransactionManager transactionManager,
            @Value("${task.timeout.scan-limit:100}") int scanLimit,
            @Value("${task.timeout.confirmation-reminder-hours:24}") int confirmationReminderHours,
            @Value("${task.timeout.transition-grace-hours:24}") int transitionGraceHours) {
        this.taskMapper = taskMapper;
        this.taskHistoryService = taskHistoryService;
        this.notificationService = notificationService;
        this.creditService = creditService;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
        return processUnacceptedTimeouts(now) + processConfirmationTimeouts(now);
    }

    private int processUnacceptedTimeouts(LocalDateTime now) {
        int processed = 0;
        List<Task> overdueTasks = taskMapper.selectOpenTasksNeedingTimeoutReminder(
                now, TaskHistoryEventType.OPEN_TIMEOUT_REMINDER, scanLimit);
        for (Task candidate : overdueTasks) {
            processed += transactions.execute(transaction -> remindUnacceptedTask(candidate.getId(), now));
        }

        LocalDateTime reminderCreatedBefore = now.minusHours(transitionGraceHours);
        List<Task> tasksToCancel = taskMapper.selectTasksReadyForTimeoutTransition(
                TaskStatus.OPEN, TaskHistoryEventType.OPEN_TIMEOUT_REMINDER, reminderCreatedBefore, scanLimit);
        for (Task candidate : tasksToCancel) {
            processed += transactions.execute(transaction -> cancelUnacceptedTask(candidate.getId(), reminderCreatedBefore));
        }
        return processed;
    }

    private int processConfirmationTimeouts(LocalDateTime now) {
        int processed = 0;
        LocalDateTime confirmationBefore = now.minusHours(confirmationReminderHours);
        List<Task> unconfirmedTasks = taskMapper.selectUnconfirmedTasksNeedingTimeoutReminder(
                confirmationBefore, TaskHistoryEventType.CONFIRMATION_TIMEOUT_REMINDER, scanLimit);
        for (Task candidate : unconfirmedTasks) {
            processed += transactions.execute(transaction -> remindUnconfirmedTask(candidate.getId(), confirmationBefore));
        }

        LocalDateTime reminderCreatedBefore = now.minusHours(transitionGraceHours);
        List<Task> tasksToComplete = taskMapper.selectTasksReadyForTimeoutTransition(
                TaskStatus.PENDING_CONFIRMATION, TaskHistoryEventType.CONFIRMATION_TIMEOUT_REMINDER,
                reminderCreatedBefore, scanLimit);
        for (Task candidate : tasksToComplete) {
            processed += transactions.execute(transaction -> completeUnconfirmedTask(candidate.getId(), reminderCreatedBefore));
        }
        return processed;
    }

    private int remindUnacceptedTask(Long taskId, LocalDateTime now) {
        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || task.getStatus() != TaskStatus.OPEN
                || task.getDeadline() == null || task.getDeadline().isAfter(now)
                || taskHistoryService.hasReminder(
                        taskId, TaskHistoryEventType.OPEN_TIMEOUT_REMINDER, task.getUpdatedAt(), null)) {
            return 0;
        }

        taskHistoryService.recordHistory(taskId, TaskStatus.OPEN, TaskStatus.OPEN, null,
                TaskHistoryEventType.OPEN_TIMEOUT_REMINDER, null);
        notificationService.notifyOpenTaskTimeoutReminder(task);
        return 1;
    }

    private int cancelUnacceptedTask(Long taskId, LocalDateTime reminderCreatedBefore) {
        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || task.getStatus() != TaskStatus.OPEN
                || !taskHistoryService.hasReminder(
                        taskId, TaskHistoryEventType.OPEN_TIMEOUT_REMINDER, task.getUpdatedAt(), reminderCreatedBefore)) {
            return 0;
        }

        LocalDateTime updatedAt = LocalDateTime.now();
        if (taskMapper.updateStatusById(taskId, TaskStatus.OPEN, TaskStatus.CANCELLED, updatedAt) != 1) {
            return 0;
        }
        String reason = "Task timed out before a helper was accepted.";
        taskHistoryService.recordHistory(taskId, TaskStatus.OPEN, TaskStatus.CANCELLED, null,
                TaskHistoryEventType.OPEN_TIMEOUT_CANCELLED, reason);
        task.setStatus(TaskStatus.CANCELLED);
        task.setUpdatedAt(updatedAt);
        notificationService.notifyTaskStatusChanged(task, TaskStatus.CANCELLED, null, reason);
        return 1;
    }

    private int remindUnconfirmedTask(Long taskId, LocalDateTime confirmationBefore) {
        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || task.getStatus() != TaskStatus.PENDING_CONFIRMATION
                || task.getUpdatedAt() == null || task.getUpdatedAt().isAfter(confirmationBefore)
                || taskHistoryService.hasReminder(
                        taskId, TaskHistoryEventType.CONFIRMATION_TIMEOUT_REMINDER, task.getUpdatedAt(), null)) {
            return 0;
        }

        taskHistoryService.recordHistory(taskId, TaskStatus.PENDING_CONFIRMATION, TaskStatus.PENDING_CONFIRMATION, null,
                TaskHistoryEventType.CONFIRMATION_TIMEOUT_REMINDER, null);
        notificationService.notifyTaskConfirmationReminder(task);
        return 1;
    }

    private int completeUnconfirmedTask(Long taskId, LocalDateTime reminderCreatedBefore) {
        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || task.getStatus() != TaskStatus.PENDING_CONFIRMATION
                || !taskHistoryService.hasReminder(
                        taskId, TaskHistoryEventType.CONFIRMATION_TIMEOUT_REMINDER,
                        task.getUpdatedAt(), reminderCreatedBefore)) {
            return 0;
        }

        LocalDateTime updatedAt = LocalDateTime.now();
        if (taskMapper.updateStatusById(
                taskId, TaskStatus.PENDING_CONFIRMATION, TaskStatus.COMPLETED, updatedAt) != 1) {
            return 0;
        }
        creditService.awardTaskReward(task);
        String reason = "Requester did not confirm before timeout.";
        taskHistoryService.recordHistory(taskId, TaskStatus.PENDING_CONFIRMATION, TaskStatus.COMPLETED, null,
                TaskHistoryEventType.CONFIRMATION_TIMEOUT_COMPLETED, reason);
        task.setStatus(TaskStatus.COMPLETED);
        task.setUpdatedAt(updatedAt);
        notificationService.notifyTaskStatusChanged(task, TaskStatus.COMPLETED, null, reason);
        return 1;
    }
}
