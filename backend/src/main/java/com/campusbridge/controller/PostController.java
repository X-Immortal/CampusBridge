package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.request.CreatePostRequest;
import com.campusbridge.dto.request.CreateReplyRequest;
import com.campusbridge.dto.request.UpdatePostRequest;
import com.campusbridge.dto.view.MyReplyPostView;
import com.campusbridge.dto.view.PostDetailView;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.dto.view.ReplyView;
import com.campusbridge.service.PostService;
import com.campusbridge.service.ReplyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {
    private final CurrentUserResolver currentUserResolver;
    private final PostService postService;
    private final ReplyService replyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostView> createPost(
            HttpServletRequest servletRequest,
            @RequestBody CreatePostRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(postService.createPost(currentUserId, request));
    }

    @GetMapping
    public ApiResponse<PagedResult<PostView>> listPosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ApiResponse.success(postService.listPosts(keyword, category, status, page, size));
    }

    @GetMapping("/replied")
    public ApiResponse<PagedResult<MyReplyPostView>> listMyRepliedPosts(
            HttpServletRequest servletRequest,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(postService.listPostsRepliedByUser(
                currentUserId, keyword, category, status, page, size));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostDetailView> getPostDetail(
            HttpServletRequest servletRequest,
            @PathVariable Long postId) {
        Long currentUserId = currentUserResolver.resolveOptionalUserId(servletRequest);
        return ApiResponse.success(postService.getPostDetail(postId, currentUserId));
    }

    @PatchMapping("/{postId}")
    public ApiResponse<PostView> updatePost(
            HttpServletRequest servletRequest,
            @PathVariable Long postId,
            @RequestBody UpdatePostRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(postService.updatePost(currentUserId, postId, request));
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deletePost(
            HttpServletRequest servletRequest,
            @PathVariable Long postId) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        postService.deletePost(currentUserId, postId);
        return ApiResponse.success();
    }

    @PostMapping("/{postId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReplyView> createReply(
            HttpServletRequest servletRequest,
            @PathVariable Long postId,
            @RequestBody CreateReplyRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(replyService.createReply(currentUserId, postId, request));
    }
}
