package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.entity.Conversation;
import com.campusbridge.entity.Message;
import com.campusbridge.entity.Post;
import com.campusbridge.entity.Reply;
import com.campusbridge.entity.Review;
import com.campusbridge.entity.Task;
import com.campusbridge.entity.TaskApplication;
import com.campusbridge.entity.User;
import com.campusbridge.mapper.ConversationMapper;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private static final int MAX_TITLE_IN_MESSAGE = 40;

    private static final String CONVERSATION_TYPE_SYSTEM = "SYSTEM";
    private static final String RELATED_TYPE_TASK = "TASK";
    private static final String RELATED_TYPE_POST = "POST";
    private static final String MESSAGE_TYPE_SYSTEM_NOTIFICATION = "SYSTEM_NOTIFICATION";
    private static final String TITLE_TASK_NOTIFICATION = "任务通知";
    private static final String TITLE_POST_NOTIFICATION = "帖子通知";
    private static final String STATUS_DISPUTED = "DISPUTED";
    private static final String REMINDER_OPEN_TIMEOUT = "TIMEOUT_REMINDER_OPEN";
    private static final String REMINDER_PENDING_CONFIRMATION =
            "TIMEOUT_REMINDER_PENDING_CONFIRMATION";

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;

    @Override
    public void notifyPostReplyCreated(
            Post post,
            Reply reply,
            Reply parentReply,
            Long operatorId) {
        if (post == null || post.getId() == null || reply == null || reply.getId() == null) {
            return;
        }

        Long recipientId = parentReply == null ? post.getAuthorId() : parentReply.getAuthorId();
        String action = parentReply == null ? "收到新回复" : "中的回复收到二级回复";
        String content = postLabel(post)
                + action
                + "："
                + abbreviate(reply.getContent() == null ? "" : reply.getContent().trim());
        sendPostNotification(recipientId, operatorId, post, content);
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
                + "收到来自"
                + userLabel(application.getApplicantId())
                + "的接单申请。";
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
        String content = "你对"
                + taskLabel(task)
                + "的接单申请已被接受。";
        sendTaskNotification(application.getApplicantId(), operatorId, task, content);
    }

    @Override
    public void notifyTaskApplicationRejected(
            Task task,
            TaskApplication application,
            Long operatorId) {
        if (task == null || application == null) {
            return;
        }
        String content = "你对"
                + taskLabel(task)
                + "的接单申请未被选中。";
        sendTaskNotification(application.getApplicantId(), operatorId, task, content);
    }

    @Override
    public void notifyTaskApplicationWithdrawn(
            Task task,
            TaskApplication application,
            Long operatorId) {
        if (task == null || application == null) {
            return;
        }
        String content = userLabel(application.getApplicantId())
                + "撤回了对"
                + taskLabel(task)
                + "的接单申请。";
        sendTaskNotification(task.getRequesterId(), operatorId, task, content);
    }

    @Override
    public void notifyTaskReviewCreated(
            Task task,
            Review review,
            Long operatorId) {
        if (task == null || review == null || review.getRevieweeId() == null) {
            return;
        }
        String content = taskLabel(task)
                + "收到了一条"
                + review.getRating()
                + "星评价。";
        sendTaskNotification(review.getRevieweeId(), operatorId, task, content);
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

    @Override
    public void notifyTaskTimeoutReminder(
            Task task,
            Long recipientId,
            Long operatorId,
            String reminderType) {
        if (task == null || task.getId() == null || recipientId == null) {
            return;
        }
        String content = buildTimeoutReminderContent(task, reminderType);
        sendTaskNotification(recipientId, operatorId, task, content);
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

        LocalDateTime now = LocalDateTime.now();
        Conversation conversation = ensureSystemConversation(
                recipientId, operatorId, RELATED_TYPE_TASK, task.getId(), TITLE_TASK_NOTIFICATION, now);

        // Acquire the parent write lock before inserting its child message.
        conversationMapper.updateLastMessageAt(conversation.getId(), operatorId, now);
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
    }

    private void sendPostNotification(
            Long recipientId,
            Long operatorId,
            Post post,
            String content) {
        if (recipientId == null || post == null || post.getId() == null || isBlank(content)) {
            return;
        }
        if (operatorId != null && operatorId.equals(recipientId)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Conversation conversation = ensureSystemConversation(
                recipientId, operatorId, RELATED_TYPE_POST, post.getId(), TITLE_POST_NOTIFICATION, now);

        // Acquire the parent write lock before inserting its child message.
        conversationMapper.updateLastMessageAt(conversation.getId(), operatorId, now);
        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(operatorId);
        message.setType(MESSAGE_TYPE_SYSTEM_NOTIFICATION);
        message.setContent(content);
        message.setReadAt(null);
        message.setCreatedAt(now);
        if (messageMapper.insert(message) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create post notification");
        }
    }

    private Conversation ensureSystemConversation(
            Long recipientId, Long operatorId, String relatedType, Long relatedId,
            String title, LocalDateTime createdAt) {
        Conversation existing = conversationMapper.selectByOwnerAndRelated(
                recipientId, CONVERSATION_TYPE_SYSTEM, relatedType, relatedId);
        if (existing != null) {
            return existing;
        }
        Conversation conversation = new Conversation();
        conversation.setType(CONVERSATION_TYPE_SYSTEM);
        conversation.setTitle(title);
        conversation.setOwnerId(recipientId);
        conversation.setPeerUserId(operatorId);
        conversation.setRelatedType(relatedType);
        conversation.setRelatedId(relatedId);
        conversation.setReplyable(0);
        conversation.setLastMessageAt(createdAt);
        conversation.setCreatedAt(createdAt);
        try {
            if (conversationMapper.insert(conversation) != 1) {
                throw new BusinessException(
                        ErrorCode.COMMON_999_INTERNAL_ERROR, "Failed to create notification conversation");
            }
            return conversation;
        } catch (DuplicateKeyException ex) {
            existing = conversationMapper.selectByOwnerAndRelatedCurrent(
                    recipientId, CONVERSATION_TYPE_SYSTEM, relatedType, relatedId);
            if (existing == null) {
                throw ex;
            }
            return existing;
        }
    }

    private String buildStatusContent(
            Task task,
            String fromStatus,
            String toStatus,
            String reason) {
        String prefix;
        if (STATUS_DISPUTED.equals(toStatus)) {
            prefix = taskLabel(task) + "已进入争议。";
        } else {
            prefix = taskLabel(task)
                    + "状态已从“"
                    + statusLabel(fromStatus)
                    + "”变更为“"
                    + statusLabel(toStatus)
                    + "”。";
        }
        if (isBlank(reason)) {
            return prefix;
        }
        return prefix + "原因：" + reason.trim();
    }

    private String buildTimeoutReminderContent(Task task, String reminderType) {
        if (REMINDER_OPEN_TIMEOUT.equals(reminderType)) {
            return taskLabel(task)
                    + "已超过截止时间且还没有被接单。"
                    + "系统将在宽限期后自动取消该任务。"
                    + "在此之前，你可以编辑、重发或手动取消。";
        }
        if (REMINDER_PENDING_CONFIRMATION.equals(reminderType)) {
            return taskLabel(task)
                    + "正在等待你的完成确认。"
                    + "如果你没有确认、驳回或发起争议，"
                    + "系统将在宽限期后自动确认完成。";
        }
        return taskLabel(task)
                + "触发了超时规则，请及时处理。";
    }

    private String taskLabel(Task task) {
        String label = "任务 #" + task.getId();
        if (isBlank(task.getTitle())) {
            return label;
        }
        return label + "“" + abbreviate(task.getTitle().trim()) + "”";
    }

    private String postLabel(Post post) {
        String label = "帖子 #" + post.getId();
        if (isBlank(post.getTitle())) {
            return label;
        }
        return label + "「" + abbreviate(post.getTitle().trim()) + "」";
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

    private String statusLabel(String status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case "DRAFT":
                return "草稿";
            case "OPEN":
                return "待接单";
            case "PENDING_REVIEW":
                return "待审核";
            case "ACCEPTED":
                return "已接单";
            case "IN_PROGRESS":
                return "进行中";
            case "PENDING_CONFIRMATION":
                return "待确认";
            case "COMPLETED":
                return "已完成";
            case "REVIEWED":
                return "已评价";
            case "CANCELLED":
                return "已取消";
            case "DISPUTED":
                return "争议中";
            case "REJECTED":
                return "已拒绝";
            default:
                return status;
        }
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
