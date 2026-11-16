package com.campusbridge.unit;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.request.CreateConversationRequest;
import com.campusbridge.dto.request.SendMessageRequest;
import com.campusbridge.dto.view.ConversationView;
import com.campusbridge.dto.view.ReadAllConversationsView;
import com.campusbridge.dto.view.UnreadSummaryView;
import com.campusbridge.entity.Attachment;
import com.campusbridge.entity.Conversation;
import com.campusbridge.entity.Message;
import com.campusbridge.entity.User;
import com.campusbridge.enums.MessageType;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.ConversationMapper;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.mapper.UserBlockMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.impl.ConversationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BE13ConversationReadServiceImplTest {
    private static final Long OWNER_ID = 1L;
    private static final Long CONVERSATION_ID = 10L;

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserBlockMapper userBlockMapper;

    @Mock
    private AttachmentMapper attachmentMapper;

    private ConversationServiceImpl conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationServiceImpl(
                conversationMapper,
                messageMapper,
                userMapper,
                userBlockMapper,
                attachmentMapper);
    }

    @Test
    void listMessagesMarksOwnedConversationUnreadMessagesRead() {
        when(conversationMapper.selectOwnedById(CONVERSATION_ID, OWNER_ID))
                .thenReturn(conversation());
        when(messageMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Message> result = invocation.getArgument(0);
            result.setRecords(List.of(message()));
            result.setTotal(1);
            return result;
        });

        PagedResult<Message> result = conversationService.listMessages(
                OWNER_ID,
                CONVERSATION_ID,
                1,
                20);

        assertThat(result.getItems()).hasSize(1);
        verify(messageMapper).markUnreadAsRead(eq(CONVERSATION_ID), any(LocalDateTime.class));
    }

    @Test
    void markConversationReadUpdatesUnreadMessagesAndReturnsFreshConversation() {
        when(conversationMapper.selectOwnedById(CONVERSATION_ID, OWNER_ID))
                .thenReturn(conversation());
        ConversationView view = conversationView();
        view.setUnreadCount(0);
        when(conversationMapper.selectOwnedViewById(CONVERSATION_ID, OWNER_ID)).thenReturn(view);

        ConversationView updated = conversationService.markConversationRead(OWNER_ID, CONVERSATION_ID);

        assertThat(updated.getUnreadCount()).isZero();
        verify(messageMapper).markUnreadAsRead(eq(CONVERSATION_ID), any(LocalDateTime.class));
    }

    @Test
    void markAllConversationsReadReturnsUpdatedUnreadCount() {
        when(messageMapper.markOwnerUnreadAsRead(eq(OWNER_ID), any(LocalDateTime.class))).thenReturn(5);
        when(messageMapper.countOwnerUnread(OWNER_ID)).thenReturn(0L);

        ReadAllConversationsView result = conversationService.markAllConversationsRead(OWNER_ID);

        assertThat(result.updatedCount()).isEqualTo(5);
        assertThat(result.unreadCount()).isZero();
    }

    @Test
    void getUnreadSummaryReturnsOwnerUnreadCount() {
        when(messageMapper.countOwnerUnreadBySystemType(OWNER_ID, true)).thenReturn(3L);
        when(messageMapper.countOwnerUnreadBySystemType(OWNER_ID, false)).thenReturn(4L);

        UnreadSummaryView result = conversationService.getUnreadSummary(OWNER_ID);

        assertThat(result.total()).isEqualTo(7L);
        assertThat(result.system()).isEqualTo(3L);
        assertThat(result.privateCount()).isEqualTo(4L);
    }

    @Test
    void markConversationReadRejectsConversationNotOwnedByCurrentUser() {
        when(conversationMapper.selectOwnedById(CONVERSATION_ID, OWNER_ID)).thenReturn(null);

        assertThatThrownBy(() -> conversationService.markConversationRead(OWNER_ID, CONVERSATION_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONV_001_NOT_FOUND));
    }

    @Test
    void sendMessageRejectsConversationWhenEitherUserHasBlockedTheOther() {
        when(userMapper.selectById(2L)).thenReturn(enabledPeer());
        when(conversationMapper.selectOwnedById(CONVERSATION_ID, OWNER_ID))
                .thenReturn(conversation());
        when(userBlockMapper.existsEitherDirection(OWNER_ID, 2L)).thenReturn(true);

        SendMessageRequest request = new SendMessageRequest();
        request.setType(MessageType.TEXT);
        request.setContent("hello");

        assertThatThrownBy(() -> conversationService.sendMessage(OWNER_ID, CONVERSATION_ID, request))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONV_003_BLOCKED));
    }

    @Test
    void imageMessageBindsOwnedTemporaryAttachmentToCreatedMessage() {
        when(userMapper.selectById(2L)).thenReturn(enabledPeer());
        Conversation ownerConversation = conversation();
        Conversation peerConversation = conversation();
        peerConversation.setId(11L);
        peerConversation.setOwnerId(2L);
        peerConversation.setPeerUserId(OWNER_ID);

        Attachment attachment = new Attachment();
        attachment.setId(30L);
        attachment.setUploaderId(OWNER_ID);
        attachment.setOwnerType("MESSAGE");
        attachment.setType("IMAGE");
        attachment.setStatus("TEMPORARY");
        attachment.setFileUrl("https://cdn.example/image.png");

        when(conversationMapper.selectOwnedById(CONVERSATION_ID, OWNER_ID)).thenReturn(ownerConversation);
        when(conversationMapper.selectPrivateByOwnerAndPeer(2L, OWNER_ID))
                .thenReturn(peerConversation);
        when(attachmentMapper.selectById(30L)).thenReturn(attachment);
        when(attachmentMapper.bindTemporaryToOwner(30L, OWNER_ID, "MESSAGE", 100L)).thenReturn(1);
        when(messageMapper.insert(any(Message.class))).thenAnswer(invocation -> {
            Message inserted = invocation.getArgument(0);
            if (inserted.getId() == null) {
                inserted.setId(inserted.getConversationId().equals(CONVERSATION_ID) ? 100L : 101L);
            }
            return 1;
        });

        SendMessageRequest request = new SendMessageRequest();
        request.setType(MessageType.IMAGE);
        request.setContent(attachment.getFileUrl());
        request.setAttachmentIds(List.of(30L));

        conversationService.sendMessage(OWNER_ID, CONVERSATION_ID, request);

        verify(attachmentMapper).bindTemporaryToOwner(30L, OWNER_ID, "MESSAGE", 100L);
    }

    @Test
    void createConversationReusesTheSamePrivateConversationAcrossDifferentContexts() {
        Conversation ownerConversation = conversation();
        Conversation peerConversation = conversation();
        peerConversation.setId(11L);
        peerConversation.setOwnerId(2L);
        peerConversation.setPeerUserId(OWNER_ID);

        User peerUser = new User();
        peerUser.setId(2L);
        peerUser.setEnabled(1);
        when(userMapper.selectById(2L)).thenReturn(peerUser);
        when(conversationMapper.selectPrivateByOwnerAndPeer(OWNER_ID, 2L))
                .thenReturn(ownerConversation);
        when(conversationMapper.selectPrivateByOwnerAndPeer(2L, OWNER_ID))
                .thenReturn(peerConversation);

        CreateConversationRequest request = new CreateConversationRequest();
        request.setPeerUserId(2L);
        request.setRelatedType("POST");
        request.setRelatedId(99L);

        when(conversationMapper.selectOwnedViewById(CONVERSATION_ID, OWNER_ID))
                .thenReturn(conversationView());
        ConversationView result = conversationService.createConversation(OWNER_ID, request);

        assertThat(result.getId()).isEqualTo(CONVERSATION_ID);
        assertThat(result.getRelatedType()).isEqualTo("TASK");
        verify(conversationMapper).selectPrivateByOwnerAndPeer(OWNER_ID, 2L);
        verify(conversationMapper).selectPrivateByOwnerAndPeer(2L, OWNER_ID);
    }

    private User enabledPeer() {
        User user = new User();
        user.setId(2L);
        user.setEnabled(1);
        return user;
    }

    @Test
    void sendingToDisabledPeerIsRejected() {
        User peer = enabledPeer();
        peer.setEnabled(0);
        when(userMapper.selectById(2L)).thenReturn(peer);
        when(conversationMapper.selectOwnedById(CONVERSATION_ID, OWNER_ID)).thenReturn(conversation());
        SendMessageRequest request = new SendMessageRequest();
        request.setType(MessageType.TEXT);
        request.setContent("Hello");
        assertThatThrownBy(() -> conversationService.sendMessage(OWNER_ID, CONVERSATION_ID, request))
                .isInstanceOf(BusinessException.class);
    }

    private Conversation conversation() {
        Conversation conversation = new Conversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setOwnerId(OWNER_ID);
        conversation.setPeerUserId(2L);
        conversation.setType("PRIVATE");
        conversation.setRelatedType("TASK");
        conversation.setRelatedId(20L);
        conversation.setReplyable(1);
        return conversation;
    }

    private ConversationView conversationView() {
        ConversationView view = new ConversationView();
        view.setId(CONVERSATION_ID);
        view.setOwnerId(OWNER_ID);
        view.setPeerUserId(2L);
        view.setType("PRIVATE");
        view.setRelatedType("TASK");
        view.setRelatedId(20L);
        view.setReplyable(1);
        return view;
    }

    private Message message() {
        Message message = new Message();
        message.setId(100L);
        message.setConversationId(CONVERSATION_ID);
        message.setSenderId(2L);
        message.setType("TEXT");
        message.setContent("我已到达取件点。");
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }
}
