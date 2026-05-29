package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.mapper.ConversationMapper;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.pojo.Conversation;
import com.campusbridge.pojo.Message;
import com.campusbridge.service.ConversationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Transactional
@Service
public class ConversationServiceImpl implements ConversationService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_INITIAL_MESSAGE_LENGTH = 1000;
    private static final int MAX_ATTACHMENT_COUNT = 9;

    private static final String TYPE_PRIVATE = "PRIVATE";
    private static final String TYPE_TASK = "TASK";
    private static final String RELATED_POST = "POST";
    private static final String RELATED_TASK = "TASK";

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    public ConversationServiceImpl(
            ConversationMapper conversationMapper,
            MessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public ResponseDtos.PagedResult<Conversation> listConversations(
            Long ownerId,
            Integer page,
            Integer size) {
        requireUserId(ownerId);
        PageWindow window = toPageWindow(page, size);
        List<Conversation> conversations =
                conversationMapper.selectByOwnerId(ownerId, window.offset, window.size);
        long total = conversationMapper.countByOwnerId(ownerId);
        return new ResponseDtos.PagedResult<>(
                conversations,
                window.page,
                window.size,
                total);
    }

    @Override
    public Conversation createConversation(
            Long ownerId,
            RequestDtos.CreateConversationRequest request) {
        requireUserId(ownerId);
        validateCreateConversationRequest(ownerId, request);

        LocalDateTime now = LocalDateTime.now();
        String relatedType = normalizeRelatedType(request.getRelatedType());
        Conversation conversation = new Conversation();
        conversation.setType(resolveConversationType(relatedType));
        conversation.setTitle(buildConversationTitle(relatedType, request.getRelatedId()));
        conversation.setOwnerId(ownerId);
        conversation.setPeerUserId(request.getPeerUserId());
        conversation.setRelatedType(relatedType);
        conversation.setRelatedId(request.getRelatedId());
        conversation.setReplyable(1);
        conversation.setLastMessageAt(now);
        conversation.setCreatedAt(now);

        int affectedRows = conversationMapper.insert(conversation);
        if (affectedRows != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create conversation");
        }

        if (!isBlank(request.getInitialMessage())) {
            Message message = buildMessage(
                    conversation.getId(),
                    ownerId,
                    ApiEnums.MessageType.TEXT.name(),
                    request.getInitialMessage(),
                    now);
            insertMessage(message);
        }
        return conversation;
    }

    @Override
    public ResponseDtos.PagedResult<Message> listMessages(
            Long ownerId,
            Long conversationId,
            Integer page,
            Integer size) {
        requireUserId(ownerId);
        Conversation conversation = requireOwnedConversation(ownerId, conversationId);
        LocalDateTime readAt = LocalDateTime.now();
        messageMapper.markUnreadAsRead(conversation.getId(), readAt);

        PageWindow window = toPageWindow(page, size);
        List<Message> messages =
                messageMapper.selectByConversationId(conversation.getId(), window.offset, window.size);
        long total = messageMapper.countByConversationId(conversation.getId());
        return new ResponseDtos.PagedResult<>(
                messages,
                window.page,
                window.size,
                total);
    }

    @Override
    public Message sendMessage(
            Long senderId,
            Long conversationId,
            RequestDtos.SendMessageRequest request) {
        requireUserId(senderId);
        validateSendMessageRequest(request);

        Conversation conversation = requireOwnedConversation(senderId, conversationId);
        if (!Integer.valueOf(1).equals(conversation.getReplyable())) {
            throw new BusinessException(ErrorCode.CONV_002_NOT_REPLYABLE);
        }

        LocalDateTime now = LocalDateTime.now();
        Message message = buildMessage(
                conversation.getId(),
                senderId,
                request.getType().name(),
                request.getContent(),
                now);
        insertMessage(message);
        conversationMapper.updateLastMessageAt(conversation.getId(), senderId, now);
        return message;
    }

    private void validateCreateConversationRequest(
            Long ownerId,
            RequestDtos.CreateConversationRequest request) {
        if (request == null) {
            throwValidation("Request body must not be null");
        }
        if (request.getPeerUserId() == null) {
            throwValidation("peerUserId is required");
        }
        if (ownerId.equals(request.getPeerUserId())) {
            throwValidation("peerUserId must not be current user");
        }
        if (isBlank(request.getRelatedType())) {
            throwValidation("relatedType is required");
        }
        if (request.getRelatedId() == null) {
            throwValidation("relatedId is required");
        }
        normalizeRelatedType(request.getRelatedType());
        if (request.getInitialMessage() != null
                && request.getInitialMessage().length() > MAX_INITIAL_MESSAGE_LENGTH) {
            throwValidation("initialMessage is too long");
        }
    }

    private void validateSendMessageRequest(RequestDtos.SendMessageRequest request) {
        if (request == null) {
            throwValidation("Request body must not be null");
        }
        if (request.getType() == null) {
            throwValidation("type is required");
        }
        if (request.getType() == ApiEnums.MessageType.SYSTEM_NOTIFICATION) {
            throwValidation("system notification cannot be sent manually");
        }
        if (isBlank(request.getContent())) {
            throwValidation("content is required");
        }
        if (request.getContent().length() > MAX_MESSAGE_LENGTH) {
            throwValidation("content is too long");
        }
        if (request.getAttachmentIds() != null
                && request.getAttachmentIds().size() > MAX_ATTACHMENT_COUNT) {
            throwValidation("attachmentIds exceeds limit");
        }
    }

    private Conversation requireOwnedConversation(Long ownerId, Long conversationId) {
        if (conversationId == null) {
            throwValidation("conversationId is required");
        }
        Conversation conversation = conversationMapper.selectOwnedById(conversationId, ownerId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CONV_001_NOT_FOUND);
        }
        return conversation;
    }

    private Message buildMessage(
            Long conversationId,
            Long senderId,
            String type,
            String content,
            LocalDateTime createdAt) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setType(type);
        message.setContent(content);
        message.setReadAt(null);
        message.setCreatedAt(createdAt);
        return message;
    }

    private void insertMessage(Message message) {
        int affectedRows = messageMapper.insert(message);
        if (affectedRows != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create message");
        }
    }

    private String normalizeRelatedType(String relatedType) {
        String normalized = relatedType.trim().toUpperCase(Locale.ROOT);
        if (!RELATED_POST.equals(normalized) && !RELATED_TASK.equals(normalized)) {
            throwValidation("relatedType must be POST or TASK");
        }
        return normalized;
    }

    private String resolveConversationType(String relatedType) {
        if (RELATED_TASK.equals(relatedType)) {
            return TYPE_TASK;
        }
        return TYPE_PRIVATE;
    }

    private String buildConversationTitle(String relatedType, Long relatedId) {
        return relatedType + " conversation #" + relatedId;
    }

    private PageWindow toPageWindow(Integer page, Integer size) {
        int normalizedPage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int normalizedSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int offset = (normalizedPage - 1) * normalizedSize;
        return new PageWindow(normalizedPage, normalizedSize, offset);
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_001_UNAUTHORIZED);
        }
    }

    private void throwValidation(String message) {
        throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class PageWindow {
        private final int page;
        private final int size;
        private final int offset;

        PageWindow(int page, int size, int offset) {
            this.page = page;
            this.size = size;
            this.offset = offset;
        }
    }
}
