package com.campusbridge.service.impl;

import com.campusbridge.enums.ContentItemType;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.request.CreateConversationRequest;
import com.campusbridge.dto.request.SendMessageRequest;
import com.campusbridge.dto.view.ConversationView;
import com.campusbridge.dto.view.ReadAllConversationsView;
import com.campusbridge.dto.view.UnreadCountView;
import com.campusbridge.entity.Attachment;
import com.campusbridge.entity.Conversation;
import com.campusbridge.entity.Message;
import com.campusbridge.entity.User;
import com.campusbridge.enums.AttachmentOwnerType;
import com.campusbridge.enums.AttachmentStatus;
import com.campusbridge.enums.ConversationType;
import com.campusbridge.enums.MessageType;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.ConversationMapper;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.mapper.UserBlockMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Transactional
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;


    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final UserBlockMapper userBlockMapper;
    private final AttachmentMapper attachmentMapper;

    @Override
    public PagedResult<ConversationView> listConversations(
            Long ownerId,
            Integer page,
            Integer size) {
        requireUserId(ownerId);
        Page<ConversationView> result = conversationMapper.selectByOwnerId(toPage(page, size), ownerId);
        return new PagedResult<>(
                result.getRecords(), (int) result.getCurrent(), (int) result.getSize(), result.getTotal());
    }

    @Override
    public ConversationView createConversation(
            Long ownerId,
            CreateConversationRequest request) {
        requireUserId(ownerId);
        validateCreateConversationRequest(ownerId, request);
        requireCommunicationAllowed(ownerId, request.getPeerUserId());

        LocalDateTime now = LocalDateTime.now();
        ContentItemType relatedType = request.getRelatedType();
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
                    MessageType.TEXT,
                    request.getInitialMessage(),
                    now,
                    now);
            Message peerMessage = buildMessage(
                    peerConversation.getId(),
                    ownerId,
                    MessageType.TEXT,
                    request.getInitialMessage(),
                    null,
                    now);
            insertMessage(ownerMessage);
            insertMessage(peerMessage);
        }
        return conversationMapper.selectOwnedViewById(conversation.getId(), ownerId);
    }

    @Override
    public PagedResult<Message> listMessages(
            Long ownerId,
            Long conversationId,
            Integer page,
            Integer size) {
        requireUserId(ownerId);
        Conversation conversation = requireOwnedConversation(ownerId, conversationId);
        LocalDateTime readAt = LocalDateTime.now();
        messageMapper.markUnreadAsRead(conversation.getId(), readAt);

        Page<Message> result = messageMapper.selectPage(toPage(page, size),
                Wrappers.<Message>lambdaQuery().eq(Message::getConversationId, conversation.getId())
                        .orderByDesc(Message::getCreatedAt, Message::getId));
        return new PagedResult<>(
                result.getRecords(), (int) result.getCurrent(), (int) result.getSize(), result.getTotal());
    }

    @Override
    public Message sendMessage(
            Long senderId,
            Long conversationId,
            SendMessageRequest request) {
        requireUserId(senderId);
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
                request.getType(),
                request.getContent(),
                now,
                now);
        insertMessage(message);
        bindMessageAttachments(message.getId(), attachments);

        Message peerMessage = buildMessage(
                peerConversation.getId(),
                senderId,
                request.getType(),
                request.getContent(),
                null,
                now);
        insertMessage(peerMessage);

        return message;
    }

    @Override
    public ConversationView markConversationRead(
            Long ownerId,
            Long conversationId) {
        requireUserId(ownerId);
        Conversation conversation = requireOwnedConversation(ownerId, conversationId);
        messageMapper.markUnreadAsRead(conversation.getId(), LocalDateTime.now());
        return conversationMapper.selectOwnedViewById(conversation.getId(), ownerId);
    }

    @Override
    public ReadAllConversationsView markAllConversationsRead(Long ownerId) {
        requireUserId(ownerId);
        int updatedCount = messageMapper.markOwnerUnreadAsRead(ownerId, LocalDateTime.now());
        return new ReadAllConversationsView(updatedCount, messageMapper.countOwnerUnread(ownerId));
    }

    @Override
    public UnreadCountView getUnreadSummary(Long ownerId) {
        requireUserId(ownerId);
        return new UnreadCountView(messageMapper.countOwnerUnread(ownerId));
    }

    private void validateCreateConversationRequest(
            Long ownerId,
            CreateConversationRequest request) {
        requireExistingEnabledUser(request.getPeerUserId());
        if (ownerId.equals(request.getPeerUserId())) {
            throwValidation("peerUserId must not be current user");
        }
    }

    private List<Attachment> validateMessageAttachments(
            Long senderId,
            SendMessageRequest request) {
        List<Long> attachmentIds = request.getAttachmentIds();
        MessageType type = request.getType();
        int attachmentCount = attachmentIds == null ? 0 : attachmentIds.size();
        if (attachmentCount != type.getAttachmentCount()) {
            throw new BusinessException(ErrorCode.FILE_004_INVALID_ATTACHMENT);
        }
        if (attachmentCount == 0) {
            return List.of();
        }

        Set<Long> uniqueIds = new HashSet<>();
        List<Attachment> attachments = attachmentIds.stream().map(id -> {
            if (id == null || !uniqueIds.add(id)) {
                throw new BusinessException(ErrorCode.FILE_004_INVALID_ATTACHMENT);
            }
            Attachment attachment = attachmentMapper.selectById(id);
            if (attachment == null
                    || !senderId.equals(attachment.getUploaderId())
                    || attachment.getStatus() != AttachmentStatus.TEMPORARY
                    || attachment.getOwnerType() != AttachmentOwnerType.MESSAGE) {
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
                    AttachmentOwnerType.MESSAGE, messageId) != 1) {
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
            MessageType type,
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

    private String buildConversationTitle(ContentItemType relatedType, Long relatedId) {
        return relatedType + " conversation #" + relatedId;
    }

    private Conversation ensureConversation(
            Long ownerId,
            Long peerUserId,
            ContentItemType relatedType,
            Long relatedId,
            LocalDateTime now) {
        Conversation conversation = conversationMapper.selectPrivateByOwnerAndPeer(
                ownerId,
                peerUserId);
        if (conversation != null) {
            return conversation;
        }

        Conversation created = new Conversation();
        created.setType(ConversationType.PRIVATE);
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
                MessageType.TEXT,
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

    private <T> Page<T> toPage(Integer page, Integer size) {
        int normalizedPage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int normalizedSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new Page<>(normalizedPage, normalizedSize);
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
}
