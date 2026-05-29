package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.mapper.ConversationMapper;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.pojo.Conversation;
import com.campusbridge.pojo.Message;
import com.campusbridge.pojo.Task;
import com.campusbridge.pojo.TaskApplication;
import com.campusbridge.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
@Service
public class NotificationServiceImpl implements NotificationService {
    private static final int MAX_TITLE_IN_MESSAGE = 40;

    private static final String CONVERSATION_TYPE_SYSTEM = "SYSTEM";
    private static final String RELATED_TYPE_TASK = "TASK";
    private static final String MESSAGE_TYPE_SYSTEM_NOTIFICATION = "SYSTEM_NOTIFICATION";
    private static final String TITLE_TASK_NOTIFICATION = "Task notifications";
    private static final String STATUS_DISPUTED = "DISPUTED";

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    public NotificationServiceImpl(
            ConversationMapper conversationMapper,
            MessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public void notifyTaskApplicationCreated(
            Task task,
            TaskApplication application,
            Long operatorId) {
        if (task == null || application == null) {
            return;
        }
        String content = taskLabel(task)
                + " received a new application from user #"
                + application.getApplicantId()
                + ".";
        sendTaskNotification(task.getRequesterId(), operatorId, task, content);
    }

    @Override
    public void notifyTaskApplicationAccepted(
            Task task,
            TaskApplication application,
            Long operatorId) {
        if (task == null || application == null) {
            return;
        }
        String content = "Your application for "
                + taskLabel(task)
                + " was accepted.";
        sendTaskNotification(application.getApplicantId(), operatorId, task, content);
    }

    @Override
    public void notifyTaskStatusChanged(
            Task task,
            String fromStatus,
            String toStatus,
            Long operatorId,
            String reason) {
        if (task == null || task.getId() == null || isBlank(fromStatus) || isBlank(toStatus)) {
            return;
        }
        String content = buildStatusContent(task, fromStatus, toStatus, reason);
        sendTaskNotification(task.getRequesterId(), operatorId, task, content);
        sendTaskNotification(task.getProviderId(), operatorId, task, content);
    }

    private void sendTaskNotification(
            Long recipientId,
            Long operatorId,
            Task task,
            String content) {
        if (recipientId == null || task == null || task.getId() == null || isBlank(content)) {
            return;
        }
        if (operatorId != null && operatorId.equals(recipientId)) {
            return;
        }

        Conversation conversation = conversationMapper.selectByOwnerAndRelated(
                recipientId,
                CONVERSATION_TYPE_SYSTEM,
                RELATED_TYPE_TASK,
                task.getId());
        LocalDateTime now = LocalDateTime.now();
        if (conversation == null) {
            conversation = createTaskNotificationConversation(recipientId, operatorId, task, now);
        }

        int duplicateCount = messageMapper.countByConversationTypeAndContent(
                conversation.getId(),
                MESSAGE_TYPE_SYSTEM_NOTIFICATION,
                content);
        if (duplicateCount > 0) {
            return;
        }

        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(operatorId);
        message.setType(MESSAGE_TYPE_SYSTEM_NOTIFICATION);
        message.setContent(content);
        message.setReadAt(null);
        message.setCreatedAt(now);

        int affectedRows = messageMapper.insert(message);
        if (affectedRows != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create notification");
        }
        conversationMapper.updateLastMessageAt(conversation.getId(), operatorId, now);
    }

    private Conversation createTaskNotificationConversation(
            Long recipientId,
            Long operatorId,
            Task task,
            LocalDateTime createdAt) {
        Conversation conversation = new Conversation();
        conversation.setType(CONVERSATION_TYPE_SYSTEM);
        conversation.setTitle(TITLE_TASK_NOTIFICATION);
        conversation.setOwnerId(recipientId);
        conversation.setPeerUserId(operatorId);
        conversation.setRelatedType(RELATED_TYPE_TASK);
        conversation.setRelatedId(task.getId());
        conversation.setReplyable(0);
        conversation.setLastMessageAt(createdAt);
        conversation.setCreatedAt(createdAt);

        int affectedRows = conversationMapper.insert(conversation);
        if (affectedRows != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create notification conversation");
        }
        return conversation;
    }

    private String buildStatusContent(
            Task task,
            String fromStatus,
            String toStatus,
            String reason) {
        String prefix;
        if (STATUS_DISPUTED.equals(toStatus)) {
            prefix = taskLabel(task) + " entered dispute.";
        } else {
            prefix = taskLabel(task)
                    + " status changed from "
                    + fromStatus
                    + " to "
                    + toStatus
                    + ".";
        }
        if (isBlank(reason)) {
            return prefix;
        }
        return prefix + " Reason: " + reason.trim();
    }

    private String taskLabel(Task task) {
        String label = "task #" + task.getId();
        if (isBlank(task.getTitle())) {
            return label;
        }
        return label + " \"" + abbreviate(task.getTitle().trim()) + "\"";
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
