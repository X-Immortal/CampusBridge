package com.campusbridge.controller;

import com.campusbridge.enums.InteractionTargetType;
import com.campusbridge.common.ApiResponse;
import com.campusbridge.dto.request.CreatePostInteractionRequest;
import com.campusbridge.dto.view.AcceptedReplyView;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.entity.PostInteraction;
import com.campusbridge.enums.PostInteractionType;
import com.campusbridge.mapper.PostInteractionMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.service.InteractionService;
import com.campusbridge.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class InteractionController {
    private final InteractionService interactionService;
    private final PostInteractionMapper postInteractionMapper;
    private final PostMapper postMapper;

    @PostMapping("/{postId}/interactions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostInteraction> createInteraction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId,
            @Valid @RequestBody CreatePostInteractionRequest request) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(interactionService.createInteraction(currentUserId, postId, request));
    }

    @DeleteMapping("/{postId}/interactions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> cancelInteraction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId,
            @RequestParam PostInteractionType type) {
        Long currentUserId = principal.getUserId();
        interactionService.cancelInteraction(currentUserId, postId, type);
        return ApiResponse.success();
    }

    @PostMapping("/{postId}/replies/{replyId}/accept")
    public ApiResponse<AcceptedReplyView> acceptReply(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId,
            @PathVariable Long replyId) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(interactionService.acceptReply(currentUserId, postId, replyId));
    }

    @GetMapping("/marked")
    public ApiResponse<List<PostView>> getMarkedPosts(@AuthenticationPrincipal UserPrincipal principal) {
        Long currentUserId = principal.getUserId();
        List<Long> postIds = postInteractionMapper.selectTargetIdsByUserAndType(
                currentUserId, InteractionTargetType.POST, PostInteractionType.MARK);
        if (postIds == null || postIds.isEmpty()) {
            return ApiResponse.success(Collections.emptyList());
        }
        List<PostView> posts = postMapper.selectPublishedByIds(postIds);
        return ApiResponse.success(posts);
    }
}
