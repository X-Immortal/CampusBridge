package com.campusbridge.controller;

import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Result;
import com.campusbridge.service.ConversationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    private final CurrentUserResolver currentUserResolver;
    private final ConversationService conversationService;

    public ConversationController(
            CurrentUserResolver currentUserResolver,
            ConversationService conversationService) {
        this.currentUserResolver = currentUserResolver;
        this.conversationService = conversationService;
    }

    @GetMapping
    public Result listConversations(
            HttpServletRequest servletRequest,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(conversationService.listConversations(currentUserId, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result createConversation(
            HttpServletRequest servletRequest,
            @RequestBody RequestDtos.CreateConversationRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(conversationService.createConversation(currentUserId, request));
    }

    @GetMapping("/{conversationId}/messages")
    public Result listMessages(
            HttpServletRequest servletRequest,
            @PathVariable Long conversationId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(
                conversationService.listMessages(currentUserId, conversationId, page, size));
    }

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Result sendMessage(
            HttpServletRequest servletRequest,
            @PathVariable Long conversationId,
            @RequestBody RequestDtos.SendMessageRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(
                conversationService.sendMessage(currentUserId, conversationId, request));
    }

    @PostMapping("/read-all")
    public Result markAllConversationsRead(HttpServletRequest servletRequest) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(conversationService.markAllConversationsRead(currentUserId));
    }

    @GetMapping("/unread-count")
    public Result getUnreadSummary(HttpServletRequest servletRequest) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(conversationService.getUnreadSummary(currentUserId));
    }

    @PostMapping("/{conversationId}/read")
    public Result markConversationRead(
            HttpServletRequest servletRequest,
            @PathVariable Long conversationId) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(
                conversationService.markConversationRead(currentUserId, conversationId));
    }
}
