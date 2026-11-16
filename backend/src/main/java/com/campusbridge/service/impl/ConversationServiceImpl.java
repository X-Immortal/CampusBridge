package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.ConversationMapper;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.mapper.UserBlockMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.pojo.Attachment;
import com.campusbridge.pojo.Conversation;
import com.campusbridge.pojo.Message;
import com.campusbridge.pojo.User;
import com.campusbridge.service.ConversationService;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private static final String RELATED_POST = "POST";
    private static final String RELATED_TASK = "TASK";

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final UserBlockMapper userBlockMapper;
    private final AttachmentMapper attachmentMapper;

    public ConversationServiceImpl(
            ConversationMapper conversationMapper,
            MessageMapper messageMapper,
            UserMapper userMapper,
            UserBlockMapper userBlockMapper,
            AttachmentMapper attachmentMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.userBlockMapper = userBlockMapper;
        this.attachmentMapper = attachmentMapper;
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
        requireCommunicationAllowed(ownerId, request.getPeerUserId());

        LocalDateTime now = LocalDateTime.now();
        String relatedType = normalizeRelatedType(request.getRelatedType());
        // Create both owner views in a stable order when users open the same chat concurrently.
        Long firstId = Math.min(ownerId, request.getPeerUserId());
        Long secondId = Math.max(ownerId, request.getPeerUserId());
        Conversation first = ensureConversation(firstId, secondId, relatedType, request.getRelatedId(), now);
        Conversation second = ensureConversation(secondId, firstId, relatedType, request.getRelatedId(), now);
        Conversation conversation = ownerId.equals(firstId) ? first : second;
        Conversation peerConversation = ownerId.equals(firstId) ? second : first;

        if (shouldInsertInitialMessage(conversation, request.getInitialMessage())) {
            touchConversationPair(conversation, peerConversation, now);
            Message ownerMessage = buildMessage(
                    conversation.getId(),
                    ownerId,
                    ApiEnums.MessageType.TEXT.name(),
                    request.getInitialMessage(),
                    now,
                    now);
            Message peerMessage = buildMessage(
                    peerConversation.getId(),
                    ownerId,
                    ApiEnums.MessageType.TEXT.name(),
                    request.getInitialMessage(),
                    null,
                    now);
            insertMessage(ownerMessage);
            insertMessage(peerMessage);
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
        List<Attachment> attachments = validateMessageAttachments(senderId, request);

        Conversation conversation = requireOwnedConversation(senderId, conversationId);
        if (!Integer.valueOf(1).equals(conversation.getReplyable())) {
            throw new BusinessException(ErrorCode.CONV_002_NOT_REPLYABLE);
        }
        requireExistingEnabledUser(conversation.getPeerUserId());
        requireCommunicationAllowed(senderId, conversation.getPeerUserId());

        LocalDateTime now = LocalDateTime.now();
        Conversation peerConversation = ensureConversation(
                conversation.getPeerUserId(),
                senderId,
                conversation.getRelatedType(),
                conversation.getRelatedId(),
                now);
        touchConversationPair(conversation, peerConversation, now);
        Message message = buildMessage(
                conversation.getId(),
                senderId,
                request.getType().name(),
                request.getContent(),
                now,
                now);
        insertMessage(message);
        bindMessageAttachments(message.getId(), attachments);

        Message peerMessage = buildMessage(
                peerConversation.getId(),
                senderId,
                request.getType().name(),
                request.getContent(),
                null,
                now);
        insertMessage(peerMessage);

        return message;
    }

    @Override
    public Conversation markConversationRead(
            Long ownerId,
            Long conversationId) {
        requireUserId(ownerId);
        Conversation conversation = requireOwnedConversation(ownerId, conversationId);
        messageMapper.markUnreadAsRead(conversation.getId(), LocalDateTime.now());
        return conversationMapper.selectOwnedById(conversation.getId(), ownerId);
    }

    @Override
    public Map<String, Object> markAllConversationsRead(Long ownerId) {
        requireUserId(ownerId);
        int updatedCount = messageMapper.markOwnerUnreadAsRead(ownerId, LocalDateTime.now());
        return Map.of(
                "updatedCount", updatedCount,
                "unreadCount", messageMapper.countOwnerUnread(ownerId));
    }

    @Override
    public Map<String, Object> getUnreadSummary(Long ownerId) {
        requireUserId(ownerId);
        long systemUnread = messageMapper.countOwnerUnreadBySystemType(ownerId, true);
        long privateUnread = messageMapper.countOwnerUnreadBySystemType(ownerId, false);
        return Map.of(
                "total", systemUnread + privateUnread,
                "system", systemUnread,
                "private", privateUnread);
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
        requireExistingEnabledUser(request.getPeerUserId());
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

    private List<Attachment> validateMessageAttachments(
            Long senderId,
            RequestDtos.SendMessageRequest request) {
        List<Long> attachmentIds = request.getAttachmentIds();
        if (request.getType() != ApiEnums.MessageType.IMAGE) {
            if (attachmentIds != null && !attachmentIds.isEmpty()) {
                throw new BusinessException(ErrorCode.FILE_004_INVALID_ATTACHMENT);
            }
            return List.of();
        }
        if (attachmentIds == null || attachmentIds.size() != 1) {
            throw new BusinessException(ErrorCode.FILE_004_INVALID_ATTACHMENT);
        }

        Set<Long> uniqueIds = new HashSet<>();
        List<Attachment> attachments = attachmentIds.stream().map(id -> {
            if (id == null || !uniqueIds.add(id)) {
                throw new BusinessException(ErrorCode.FILE_004_INVALID_ATTACHMENT);
            }
            Attachment attachment = attachmentMapper.selectById(id);
            if (attachment == null
                    || !senderId.equals(attachment.getUploaderId())
                    || !ApiEnums.AttachmentStatus.TEMPORARY.name().equals(attachment.getStatus())
                    || !ApiEnums.AttachmentOwnerType.MESSAGE.name().equals(attachment.getOwnerType())
                    || !ApiEnums.AttachmentType.IMAGE.name().equals(attachment.getType())) {
                throw new BusinessException(ErrorCode.FILE_004_INVALID_ATTACHMENT);
            }
            return attachment;
        }).toList();

        if (attachments.stream().noneMatch(item -> request.getContent().equals(item.getFileUrl()))) {
            throw new BusinessException(ErrorCode.FILE_004_INVALID_ATTACHMENT);
        }
        return attachments;
    }

    private void bindMessageAttachments(Long messageId, List<Attachment> attachments) {
        for (Attachment attachment : attachments) {
            if (attachmentMapper.bindTemporaryToOwner(
                    attachment.getId(), attachment.getUploaderId(),
                    ApiEnums.AttachmentOwnerType.MESSAGE.name(), messageId) != 1) {
                throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "Failed to bind message attachment");
            }
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

    private void requireCommunicationAllowed(Long firstUserId, Long secondUserId) {
        if (userBlockMapper.existsEitherDirection(firstUserId, secondUserId)) {
            throw new BusinessException(ErrorCode.CONV_003_BLOCKED);
        }
    }

    private Message buildMessage(
            Long conversationId,
            Long senderId,
            String type,
            String content,
            LocalDateTime readAt,
            LocalDateTime createdAt) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setType(type);
        message.setContent(content);
        message.setReadAt(readAt);
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

    private String buildConversationTitle(String relatedType, Long relatedId) {
        return relatedType + " conversation #" + relatedId;
    }

    private Conversation ensureConversation(
            Long ownerId,
            Long peerUserId,
            String relatedType,
            Long relatedId,
            LocalDateTime now) {
        Conversation conversation = conversationMapper.selectPrivateByOwnerAndPeer(
                ownerId,
                peerUserId);
        if (conversation != null) {
            return conversation;
        }

        Conversation created = new Conversation();
        created.setType(TYPE_PRIVATE);
        created.setTitle(buildConversationTitle(relatedType, relatedId));
        created.setOwnerId(ownerId);
        created.setPeerUserId(peerUserId);
        created.setRelatedType(relatedType);
        created.setRelatedId(relatedId);
        created.setReplyable(1);
        created.setLastMessageAt(now);
        created.setCreatedAt(now);

        try {
            if (conversationMapper.insert(created) != 1) {
                throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "Failed to create conversation");
            }
            return created;
        } catch (DuplicateKeyException ex) {
            Conversation existing = conversationMapper.selectPrivateByOwnerAndPeerCurrent(ownerId, peerUserId);
            if (existing == null) {
                throw ex;
            }
            return existing;
        }
    }

    private boolean shouldInsertInitialMessage(
            Conversation conversation,
            String initialMessage) {
        if (conversation == null || isBlank(initialMessage)) {
            return false;
        }
        return messageMapper.countByConversationTypeAndContent(
                conversation.getId(),
                ApiEnums.MessageType.TEXT.name(),
                initialMessage) == 0;
    }

    private void touchConversationPair(
            Conversation owner, Conversation peer, LocalDateTime messageAt) {
        // Both send directions lock the same parent rows before inserting message copies.
        Conversation first = owner.getId() < peer.getId() ? owner : peer;
        Conversation second = owner.getId() < peer.getId() ? peer : owner;
        touchConversation(first, second.getOwnerId(), messageAt);
        touchConversation(second, first.getOwnerId(), messageAt);
    }

    private void touchConversation(
            Conversation conversation,
            Long peerUserId,
            LocalDateTime lastMessageAt) {
        conversationMapper.updateLastMessageAt(
                conversation.getId(),
                peerUserId,
                lastMessageAt);
        conversation.setPeerUserId(peerUserId);
        conversation.setLastMessageAt(lastMessageAt);
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

    private void requireExistingEnabledUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_001_NOT_FOUND);
        }
        if (Integer.valueOf(0).equals(user.getEnabled())) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "peer user disabled");
        }
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
