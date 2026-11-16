package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.request.CreateConversationRequest;
import com.campusbridge.dto.request.SendMessageRequest;
import com.campusbridge.dto.view.ConversationView;
import com.campusbridge.dto.view.ReadAllConversationsView;
import com.campusbridge.dto.view.UnreadSummaryView;
import com.campusbridge.entity.Message;
import com.campusbridge.service.ConversationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private final CurrentUserResolver currentUserResolver;
    private final ConversationService conversationService;

    @GetMapping
    public ApiResponse<PagedResult<ConversationView>> listConversations(
            HttpServletRequest servletRequest,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(conversationService.listConversations(currentUserId, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConversationView> createConversation(
            HttpServletRequest servletRequest,
            @Valid @RequestBody CreateConversationRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(conversationService.createConversation(currentUserId, request));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<PagedResult<Message>> listMessages(
            HttpServletRequest servletRequest,
            @PathVariable Long conversationId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(
                conversationService.listMessages(currentUserId, conversationId, page, size));
    }

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Message> sendMessage(
            HttpServletRequest servletRequest,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(
                conversationService.sendMessage(currentUserId, conversationId, request));
    }

    @PostMapping("/read-all")
    public ApiResponse<ReadAllConversationsView> markAllConversationsRead(HttpServletRequest servletRequest) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(conversationService.markAllConversationsRead(currentUserId));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadSummaryView> getUnreadSummary(HttpServletRequest servletRequest) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(conversationService.getUnreadSummary(currentUserId));
    }

    @PostMapping("/{conversationId}/read")
    public ApiResponse<ConversationView> markConversationRead(
            HttpServletRequest servletRequest,
            @PathVariable Long conversationId) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(
                conversationService.markConversationRead(currentUserId, conversationId));
    }
}
