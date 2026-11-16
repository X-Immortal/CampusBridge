package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.dto.view.ConversationView;
import com.campusbridge.pojo.Message;

import java.util.Map;

public interface ConversationService {
    ResponseDtos.PagedResult<ConversationView> listConversations(
            Long ownerId,
            Integer page,
            Integer size);

    ConversationView createConversation(
            Long ownerId,
            RequestDtos.CreateConversationRequest request);

    ResponseDtos.PagedResult<Message> listMessages(
            Long ownerId,
            Long conversationId,
            Integer page,
            Integer size);

    Message sendMessage(
            Long senderId,
            Long conversationId,
            RequestDtos.SendMessageRequest request);

    ConversationView markConversationRead(
            Long ownerId,
            Long conversationId);

    Map<String, Object> markAllConversationsRead(Long ownerId);

    Map<String, Object> getUnreadSummary(Long ownerId);
}
