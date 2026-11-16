package com.campusbridge.service.impl;

import com.campusbridge.entity.Task;
import com.campusbridge.entity.TaskApplication;
import com.campusbridge.entity.Review;
import com.campusbridge.entity.User;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.enums.NotificationType;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.TaskNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskNotificationServiceImpl implements TaskNotificationService {
    private static final int MAX_TITLE_IN_MESSAGE = 40;
    private final NotificationService notifications;
    private final UserMapper userMapper;

    @Override
    public void notifyTaskApplicationCreated(Task task, TaskApplication application, Long operatorId) {
        NotificationType type = NotificationType.TASK_APPLICATION_CREATED;
        sendTaskNotification(task.getRequesterId(), operatorId, task, type,
                taskLabel(task) + "收到来自" + userLabel(application.getApplicantId()) + "的接单申请。");
    }

    @Override
    public void notifyTaskApplicationAccepted(Task task, TaskApplication application, Long operatorId) {
        NotificationType type = NotificationType.TASK_APPLICATION_ACCEPTED;
        sendTaskNotification(application.getApplicantId(), operatorId, task, type,
                "你对" + taskLabel(task) + "的接单申请已被接受。");
    }

    @Override
    public void notifyTaskApplicationRejected(Task task, TaskApplication application, Long operatorId) {
        NotificationType type = NotificationType.TASK_APPLICATION_REJECTED;
        sendTaskNotification(application.getApplicantId(), operatorId, task, type,
                "你对" + taskLabel(task) + "的接单申请未被选中。");
    }

    @Override
    public void notifyTaskApplicationWithdrawn(Task task, TaskApplication application, Long operatorId) {
        NotificationType type = NotificationType.TASK_APPLICATION_WITHDRAWN;
        sendTaskNotification(task.getRequesterId(), operatorId, task, type,
                userLabel(application.getApplicantId()) + "撤回了对" + taskLabel(task) + "的接单申请。");
    }

    @Override
    public void notifyTaskReviewCreated(Task task, Review review, Long operatorId) {
        NotificationType type = NotificationType.TASK_REVIEWED;
        sendTaskNotification(review.getRevieweeId(), operatorId, task, type,
                taskLabel(task) + "收到了一条" + review.getRating() + "星评价。");
    }

    @Override
    public void notifyTaskStatusChanged(Task task, TaskStatus toStatus, Long operatorId, String reason) {
        NotificationType type = switch (toStatus) {
            case OPEN -> NotificationType.TASK_PUBLISHED;
            case PENDING_REVIEW -> NotificationType.TASK_PENDING_REVIEW;
            case ACCEPTED -> NotificationType.TASK_ACCEPTED;
            case IN_PROGRESS -> NotificationType.TASK_STARTED;
            case PENDING_CONFIRMATION -> NotificationType.TASK_PENDING_CONFIRMATION;
            case COMPLETED -> NotificationType.TASK_COMPLETED;
            case REVIEWED -> NotificationType.TASK_REVIEWED;
            case CANCELLED -> NotificationType.TASK_CANCELLED;
            case DISPUTED -> NotificationType.TASK_DISPUTED;
            case REJECTED -> NotificationType.TASK_REJECTED;
            case DRAFT -> throw new IllegalArgumentException("Task state does not generate status notifications");
        };
        String title = toStatus == TaskStatus.REVIEWED ? "任务已评价" : type.getDefaultTitle();
        String content = taskLabel(task) + "：" + title + "。";
        if (reason != null && !reason.isBlank()) {
            content += "原因：" + reason.trim();
        }
        sendTaskNotification(task.getRequesterId(), operatorId, task, type, title, content);
        sendTaskNotification(task.getProviderId(), operatorId, task, type, title, content);
    }

    @Override
    public void notifyOpenTaskTimeoutReminder(Task task) {
        sendTaskNotification(task.getRequesterId(), null, task, NotificationType.TASK_OPEN_TIMEOUT,
                taskLabel(task) + "已超过截止时间且还没有被接单。系统将在宽限期后自动取消该任务。在此之前，你可以编辑、重发或手动取消。");
    }

    @Override
    public void notifyTaskConfirmationReminder(Task task) {
        sendTaskNotification(task.getRequesterId(), null, task, NotificationType.TASK_CONFIRMATION_REMINDER,
                taskLabel(task) + "正在等待你的完成确认。如果你没有确认、驳回或发起争议，系统将在宽限期后自动确认完成。");
    }

    private void sendTaskNotification(Long recipientId, Long operatorId, Task task,
                                      NotificationType type, String content) {
        sendTaskNotification(recipientId, operatorId, task, type, type.getDefaultTitle(), content);
    }

    private void sendTaskNotification(Long recipientId, Long operatorId, Task task,
                                      NotificationType type, String title, String content) {
        if (recipientId == null || recipientId.equals(operatorId)) return;
        notifications.create(recipientId, type, title, content, task.getId());
    }

    private String taskLabel(Task task) {
        String label = "任务 #" + task.getId();
        if (isBlank(task.getTitle())) {
            return label;
        }
        return label + "“" + abbreviate(task.getTitle().trim()) + "”";
    }

    private String userLabel(Long userId) {
        String label = "用户 #" + userId;
        if (userId == null) {
            return label;
        }
        User user = userMapper.selectById(userId);
        if (user == null || isBlank(user.getUsername())) {
            return label;
        }
        return label + "“" + abbreviate(user.getUsername().trim()) + "”";
    }

    private String abbreviate(String value) {
        if (value.length() <= MAX_TITLE_IN_MESSAGE) {
            return value;
        }
        return value.substring(0, MAX_TITLE_IN_MESSAGE) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
