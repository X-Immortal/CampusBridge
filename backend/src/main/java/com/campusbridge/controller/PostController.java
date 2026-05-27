package com.campusbridge.controller;

import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Result;
import com.campusbridge.service.ContentModerationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {
    private final CurrentUserResolver currentUserResolver;
    private final ContentModerationService contentModerationService;

    public PostController(
            CurrentUserResolver currentUserResolver,
            ContentModerationService contentModerationService) {
        this.currentUserResolver = currentUserResolver;
        this.contentModerationService = contentModerationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result createPost(
            HttpServletRequest servletRequest,
            @RequestBody RequestDtos.CreatePostRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        contentModerationService.assertPostPublishAllowed(currentUserId, request);
        return Result.success();
    }

    @GetMapping
    public Result listPosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success();
    }

    @GetMapping("/{postId}")
    public Result getPostDetail(@PathVariable Long postId) {
        return Result.success();
    }

    @PatchMapping("/{postId}")
    public Result updatePost(
            @PathVariable Long postId,
            @RequestBody RequestDtos.UpdatePostRequest request) {
        return Result.success();
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result deletePost(@PathVariable Long postId) {
        return Result.success();
    }

    @PostMapping("/{postId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public Result createReply(
            @PathVariable Long postId,
            @RequestBody RequestDtos.CreateReplyRequest request) {
        return Result.success();
    }
}
