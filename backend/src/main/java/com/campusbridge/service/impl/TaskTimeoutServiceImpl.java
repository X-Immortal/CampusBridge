package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.entity.Task;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.NotificationService;
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
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_PENDING_CONFIRMATION = "PENDING_CONFIRMATION";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String NOTE_OPEN_TIMEOUT_REMINDER = "TIMEOUT_REMINDER_OPEN";
    private static final String NOTE_OPEN_TIMEOUT_CANCELLED = "TIMEOUT_CANCELLED_OPEN";
    private static final String NOTE_CONFIRMATION_TIMEOUT_REMINDER = "TIMEOUT_REMINDER_PENDING_CONFIRMATION";
    private static final String NOTE_CONFIRMATION_TIMEOUT_COMPLETED = "TIMEOUT_AUTO_COMPLETED_PENDING_CONFIRMATION";

    private final TaskMapper taskMapper;
    private final TaskHistoryService taskHistoryService;
    private final NotificationService notificationService;
    private final CreditService creditService;
    private final TransactionTemplate transactions;
    private final int scanLimit;
    private final int confirmationReminderHours;
    private final int transitionGraceHours;

    public TaskTimeoutServiceImpl(
            TaskMapper taskMapper,
            TaskHistoryService taskHistoryService,
            NotificationService notificationService,
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
        if (now == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "now must not be null");
        }
        int processed = remindTasks(STATUS_OPEN, now, NOTE_OPEN_TIMEOUT_REMINDER);
        processed += transitionTasks(STATUS_OPEN, STATUS_CANCELLED,
                NOTE_OPEN_TIMEOUT_REMINDER, NOTE_OPEN_TIMEOUT_CANCELLED, now);
        processed += remindTasks(STATUS_PENDING_CONFIRMATION,
                now.minusHours(confirmationReminderHours), NOTE_CONFIRMATION_TIMEOUT_REMINDER);
        processed += transitionTasks(STATUS_PENDING_CONFIRMATION, STATUS_COMPLETED,
                NOTE_CONFIRMATION_TIMEOUT_REMINDER, NOTE_CONFIRMATION_TIMEOUT_COMPLETED, now);
        return processed;
    }

    private int remindTasks(String expectedStatus, LocalDateTime referenceTime, String reminderNote) {
        List<Task> candidates = taskMapper.selectTasksNeedingTimeoutReminder(
                expectedStatus, referenceTime, reminderNote, scanLimit);
        int processed = 0;
        for (Task candidate : candidates) {
            processed += transactions.execute(transaction -> {
                Task task = taskMapper.selectByIdForUpdate(candidate.getId());
                if (task == null || !expectedStatus.equals(task.getStatus())) {
                    return 0;
                }
                LocalDateTime relevantTime = STATUS_OPEN.equals(expectedStatus)
                        ? task.getDeadline() : task.getUpdatedAt();
                if (relevantTime == null || relevantTime.isAfter(referenceTime)
                        || taskHistoryService.hasReminder(task.getId(), reminderNote, task.getUpdatedAt(), null)) {
                    return 0;
                }
                taskHistoryService.recordHistory(
                        task.getId(), expectedStatus, expectedStatus, null, reminderNote);
                notificationService.notifyTaskTimeoutReminder(
                        task, task.getRequesterId(), null, reminderNote);
                return 1;
            });
        }
        return processed;
    }

    private int transitionTasks(
            String fromStatus, String toStatus, String reminderNote, String historyNote, LocalDateTime now) {
        LocalDateTime reminderCreatedBefore = now.minusHours(transitionGraceHours);
        List<Task> candidates = taskMapper.selectTasksReadyForTimeoutTransition(
                fromStatus, reminderNote, reminderCreatedBefore, scanLimit);
        int processed = 0;
        for (Task candidate : candidates) {
            processed += transactions.execute(transaction -> {
                Task task = taskMapper.selectByIdForUpdate(candidate.getId());
                if (task == null || !fromStatus.equals(task.getStatus())
                        || !taskHistoryService.hasReminder(
                                task.getId(), reminderNote, task.getUpdatedAt(), reminderCreatedBefore)) {
                    return 0;
                }
                LocalDateTime updatedAt = LocalDateTime.now();
                if (taskMapper.updateStatusById(task.getId(), fromStatus, toStatus, updatedAt) != 1) {
                    return 0;
                }
                if (STATUS_COMPLETED.equals(toStatus)) {
                    creditService.awardTaskReward(task);
                }
                taskHistoryService.recordHistory(task.getId(), fromStatus, toStatus, null, historyNote);
                task.setStatus(toStatus);
                task.setUpdatedAt(updatedAt);
                notificationService.notifyTaskStatusChanged(task, fromStatus, toStatus, null,
                        STATUS_COMPLETED.equals(toStatus)
                                ? "Requester did not confirm before timeout."
                                : "Task timed out before a helper was accepted.");
                return 1;
            });
        }
        return processed;
    }
}
