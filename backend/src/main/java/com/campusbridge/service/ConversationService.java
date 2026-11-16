package com.campusbridge.service;

import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.request.CreateConversationRequest;
import com.campusbridge.dto.request.SendMessageRequest;
import com.campusbridge.dto.view.ConversationView;
import com.campusbridge.dto.view.ReadAllConversationsView;
import com.campusbridge.dto.view.UnreadSummaryView;
import com.campusbridge.entity.Message;

public interface ConversationService {
    PagedResult<ConversationView> listConversations(
            Long ownerId,
            Integer page,
            Integer size);

    ConversationView createConversation(
            Long ownerId,
            CreateConversationRequest request);

    PagedResult<Message> listMessages(
            Long ownerId,
            Long conversationId,
            Integer page,
            Integer size);

    Message sendMessage(
            Long senderId,
            Long conversationId,
            SendMessageRequest request);

    ConversationView markConversationRead(
            Long ownerId,
            Long conversationId);

    ReadAllConversationsView markAllConversationsRead(Long ownerId);

    UnreadSummaryView getUnreadSummary(Long ownerId);
}
