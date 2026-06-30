package com.campusbridge.controller;

import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.PostInteractionMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.pojo.Post;
import com.campusbridge.pojo.Result;
import com.campusbridge.service.InteractionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
public class InteractionController {
    private final CurrentUserResolver currentUserResolver;
    private final InteractionService interactionService;
    private final PostInteractionMapper postInteractionMapper;
    private final PostMapper postMapper;

    public InteractionController(
            CurrentUserResolver currentUserResolver,
            InteractionService interactionService,
            PostInteractionMapper postInteractionMapper,
            PostMapper postMapper) {
        this.currentUserResolver = currentUserResolver;
        this.interactionService = interactionService;
        this.postInteractionMapper = postInteractionMapper;
        this.postMapper = postMapper;
    }

    @PostMapping("/{postId}/interactions")
    @ResponseStatus(HttpStatus.CREATED)
    public Result createInteraction(
            HttpServletRequest servletRequest,
            @PathVariable Long postId,
            @RequestBody RequestDtos.CreatePostInteractionRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(interactionService.createInteraction(currentUserId, postId, request));
    }

    @DeleteMapping("/{postId}/interactions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result cancelInteraction(
            HttpServletRequest servletRequest,
            @PathVariable Long postId,
            @RequestParam String type) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        interactionService.cancelInteraction(currentUserId, postId, type);
        return Result.success();
    }

    @PostMapping("/{postId}/replies/{replyId}/accept")
    public Result acceptReply(
            HttpServletRequest servletRequest,
            @PathVariable Long postId,
            @PathVariable Long replyId) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(interactionService.acceptReply(currentUserId, postId, replyId));
    }

    @GetMapping("/marked")
    public Result getMarkedPosts(HttpServletRequest servletRequest) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        List<Long> postIds = postInteractionMapper.selectTargetIdsByUserAndType(
                currentUserId, "POST", "MARK");
        if (postIds == null || postIds.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        List<Post> posts = postMapper.selectByIds(postIds);
        return Result.success(posts);
    }
}
