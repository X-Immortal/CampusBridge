package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.mapper.ConversationMapper;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.mapper.UserBlockMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Attachment;
import com.campusbridge.pojo.Conversation;
import com.campusbridge.pojo.Message;
import com.campusbridge.pojo.User;
import com.campusbridge.service.impl.ConversationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
                .thenReturn(conversationWithUnread(3));
        when(messageMapper.selectByConversationId(CONVERSATION_ID, 0, 20))
                .thenReturn(List.of(message()));
        when(messageMapper.countByConversationId(CONVERSATION_ID)).thenReturn(1L);

        ResponseDtos.PagedResult<Message> result = conversationService.listMessages(
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
                .thenReturn(conversationWithUnread(3), conversationWithUnread(0));

        Conversation updated = conversationService.markConversationRead(OWNER_ID, CONVERSATION_ID);

        assertThat(updated.getUnreadCount()).isZero();
        verify(messageMapper).markUnreadAsRead(eq(CONVERSATION_ID), any(LocalDateTime.class));
    }

    @Test
    void markAllConversationsReadReturnsUpdatedUnreadCount() {
        when(messageMapper.markOwnerUnreadAsRead(eq(OWNER_ID), any(LocalDateTime.class))).thenReturn(5);
        when(messageMapper.countOwnerUnread(OWNER_ID)).thenReturn(0L);

        Map<String, Object> result = conversationService.markAllConversationsRead(OWNER_ID);

        assertThat(result)
                .containsEntry("updatedCount", 5)
                .containsEntry("unreadCount", 0L);
    }

    @Test
    void getUnreadSummaryReturnsOwnerUnreadCount() {
        when(messageMapper.countOwnerUnreadBySystemType(OWNER_ID, true)).thenReturn(3L);
        when(messageMapper.countOwnerUnreadBySystemType(OWNER_ID, false)).thenReturn(4L);

        Map<String, Object> result = conversationService.getUnreadSummary(OWNER_ID);

        assertThat(result).containsEntry("total", 7L);
        assertThat(result).containsEntry("system", 3L);
        assertThat(result).containsEntry("private", 4L);
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
        when(conversationMapper.selectOwnedById(CONVERSATION_ID, OWNER_ID))
                .thenReturn(conversationWithUnread(0));
        when(userBlockMapper.existsEitherDirection(OWNER_ID, 2L)).thenReturn(true);

        RequestDtos.SendMessageRequest request = new RequestDtos.SendMessageRequest();
        request.setType(ApiEnums.MessageType.TEXT);
        request.setContent("hello");

        assertThatThrownBy(() -> conversationService.sendMessage(OWNER_ID, CONVERSATION_ID, request))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONV_003_BLOCKED));
    }

    @Test
    void imageMessageBindsOwnedTemporaryAttachmentToCreatedMessage() {
        Conversation ownerConversation = conversationWithUnread(0);
        Conversation peerConversation = conversationWithUnread(0);
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
        when(attachmentMapper.updateById(attachment)).thenReturn(1);
        when(messageMapper.insert(any(Message.class))).thenAnswer(invocation -> {
            Message inserted = invocation.getArgument(0);
            if (inserted.getId() == null) {
                inserted.setId(inserted.getConversationId().equals(CONVERSATION_ID) ? 100L : 101L);
            }
            return 1;
        });

        RequestDtos.SendMessageRequest request = new RequestDtos.SendMessageRequest();
        request.setType(ApiEnums.MessageType.IMAGE);
        request.setContent(attachment.getFileUrl());
        request.setAttachmentIds(List.of(30L));

        conversationService.sendMessage(OWNER_ID, CONVERSATION_ID, request);

        assertThat(attachment.getOwnerId()).isEqualTo(100L);
        assertThat(attachment.getStatus()).isEqualTo("BOUND");
        verify(attachmentMapper).updateById(attachment);
    }

    @Test
    void createConversationReusesTheSamePrivateConversationAcrossDifferentContexts() {
        Conversation ownerConversation = conversationWithUnread(0);
        Conversation peerConversation = conversationWithUnread(0);
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

        RequestDtos.CreateConversationRequest request = new RequestDtos.CreateConversationRequest();
        request.setPeerUserId(2L);
        request.setRelatedType("POST");
        request.setRelatedId(99L);

        Conversation result = conversationService.createConversation(OWNER_ID, request);

        assertThat(result.getId()).isEqualTo(CONVERSATION_ID);
        assertThat(result.getRelatedType()).isEqualTo("TASK");
        verify(conversationMapper).selectPrivateByOwnerAndPeer(OWNER_ID, 2L);
        verify(conversationMapper).selectPrivateByOwnerAndPeer(2L, OWNER_ID);
    }

    private Conversation conversationWithUnread(int unreadCount) {
        Conversation conversation = new Conversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setOwnerId(OWNER_ID);
        conversation.setPeerUserId(2L);
        conversation.setType("PRIVATE");
        conversation.setRelatedType("TASK");
        conversation.setRelatedId(20L);
        conversation.setReplyable(1);
        conversation.setUnreadCount(unreadCount);
        return conversation;
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
