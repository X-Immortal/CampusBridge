package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.mapper.ConversationMapper;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.pojo.Conversation;
import com.campusbridge.pojo.Message;
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

    private ConversationServiceImpl conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationServiceImpl(
                conversationMapper,
                messageMapper,
                userMapper);
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
        when(messageMapper.countOwnerUnread(OWNER_ID)).thenReturn(7L);

        Map<String, Object> result = conversationService.getUnreadSummary(OWNER_ID);

        assertThat(result).containsEntry("unreadCount", 7L);
    }

    @Test
    void markConversationReadRejectsConversationNotOwnedByCurrentUser() {
        when(conversationMapper.selectOwnedById(CONVERSATION_ID, OWNER_ID)).thenReturn(null);

        assertThatThrownBy(() -> conversationService.markConversationRead(OWNER_ID, CONVERSATION_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONV_001_NOT_FOUND));
    }

    private Conversation conversationWithUnread(int unreadCount) {
        Conversation conversation = new Conversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setOwnerId(OWNER_ID);
        conversation.setPeerUserId(2L);
        conversation.setType("TASK");
        conversation.setRelatedType("TASK");
        conversation.setRelatedId(20L);
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
