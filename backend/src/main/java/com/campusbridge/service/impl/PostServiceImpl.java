package com.campusbridge.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.dto.view.ViewDtos;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.dto.view.ReplyView;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.PostInteractionMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.pojo.Attachment;
import com.campusbridge.pojo.Post;
import com.campusbridge.pojo.PostInteraction;
import com.campusbridge.service.ContentModerationService;
import com.campusbridge.service.PostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Transactional
@Service
public class PostServiceImpl implements PostService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_ATTACHMENT_COUNT = 9;
    private static final String ATTACHMENT_OWNER_TYPE = "POST";

    private final PostMapper postMapper;
    private final ReplyMapper replyMapper;
    private final PostInteractionMapper postInteractionMapper;
    private final AttachmentMapper attachmentMapper;
    private final ContentModerationService contentModerationService;

    public PostServiceImpl(
            PostMapper postMapper,
            ReplyMapper replyMapper,
            PostInteractionMapper postInteractionMapper,
            AttachmentMapper attachmentMapper,
            ContentModerationService contentModerationService) {
        this.postMapper = postMapper;
        this.replyMapper = replyMapper;
        this.postInteractionMapper = postInteractionMapper;
        this.attachmentMapper = attachmentMapper;
        this.contentModerationService = contentModerationService;
    }

    @Override
    public PostView createPost(Long userId, RequestDtos.CreatePostRequest request) {
        validatePostRequest(request);
        contentModerationService.assertPostPublishAllowed(userId, request);

        LocalDateTime now = LocalDateTime.now();
        Post post = new Post();
        post.setAuthorId(userId);
        post.setCategory(request.getCategory().name());
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setStatus("PUBLISHED");
        post.setReplyCount(0);
        post.setLikeCount(0);
        post.setMarkCount(0);
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        if (postMapper.insert(post) != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create post");
        }
        bindAttachments(userId, post.getId(), request.getAttachmentIds());
        return PostView.from(post);
    }

    @Override
    public ResponseDtos.PagedResult<PostView> listPosts(
            String keyword, String category, String status, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        Page<PostView> result = postMapper.selectWithFilters(new Page<>(safePage, safeSize), category, status, keyword);
        return new ResponseDtos.PagedResult<>(result.getRecords(), safePage, safeSize, result.getTotal());
    }

    @Override
    public ResponseDtos.PagedResult<ViewDtos.MyReplyPostView> listPostsRepliedByUser(
            Long userId, String keyword, String category, String status, Integer page, Integer size) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_001_UNAUTHORIZED);
        }

        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        Page<PostView> result = postMapper.selectRepliedByUser(
                new Page<>(safePage, safeSize), userId, category, status, keyword);
        List<PostView> posts = result.getRecords();
        if (posts.isEmpty()) {
            return new ResponseDtos.PagedResult<>(Collections.emptyList(), safePage, safeSize, result.getTotal());
        }

        List<Long> postIds = posts.stream().map(PostView::getId).toList();
        Map<Long, List<ReplyView>> repliesByPostId = replyMapper.selectByAuthorAndPostIds(userId, postIds)
                .stream()
                .collect(Collectors.groupingBy(ReplyView::getPostId));

        List<ViewDtos.MyReplyPostView> items = posts.stream()
                .map(post -> {
                    List<ReplyView> replies = repliesByPostId.getOrDefault(post.getId(), Collections.emptyList());
                    LocalDateTime lastReplyAt = replies.stream()
                            .map(ReplyView::getCreatedAt)
                            .filter(value -> value != null)
                            .max(LocalDateTime::compareTo)
                            .orElse(null);
                    return new ViewDtos.MyReplyPostView(
                            post,
                            replies,
                            lastReplyAt,
                            (long) replies.size());
                })
                .toList();
        return new ResponseDtos.PagedResult<>(items, safePage, safeSize, result.getTotal());
    }

    @Override
    public ViewDtos.PostDetailView getPostDetail(Long postId, Long currentUserId) {
        PostView post = postMapper.selectDetailById(postId);
        if (post == null || "DELETED".equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.POST_001_NOT_FOUND);
        }
        if (!("PUBLISHED".equals(post.getStatus()) || "CLOSED".equals(post.getStatus()))
                && !post.getAuthorId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.POST_001_NOT_FOUND);
        }
        List<ReplyView> replies = replyMapper.selectByPostId(postId, 0, 50);
        boolean likedByCurrentUser = false;
        boolean markedByCurrentUser = false;
        if (currentUserId != null) {
            PostInteraction likeInteraction = postInteractionMapper.selectByUserTargetType(
                    currentUserId, "POST", postId, "LIKE");
            likedByCurrentUser = likeInteraction != null;
            PostInteraction markInteraction = postInteractionMapper.selectByUserTargetType(
                    currentUserId, "POST", postId, "MARK");
            markedByCurrentUser = markInteraction != null;
        }
        return new ViewDtos.PostDetailView(
                post, replies, likedByCurrentUser, markedByCurrentUser,
                post.getAcceptedReplyId(), attachmentMapper.selectByOwner("POST", postId));
    }

    @Override
    public PostView updatePost(Long userId, Long postId, RequestDtos.UpdatePostRequest request) {
        Post post = requirePostExists(postId);
        if (!userId.equals(post.getAuthorId())) {
            throw new BusinessException(ErrorCode.POST_002_PERMISSION_DENIED);
        }

        if (request == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "Request body is required");
        }
        String expectedStatus = post.getStatus();
        if (request.getTitle() != null) {
            post.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            post.setContent(request.getContent());
        }
        if (request.getStatus() != null) {
            boolean authorStatus = request.getStatus() == ApiEnums.PostStatus.PUBLISHED
                    || request.getStatus() == ApiEnums.PostStatus.CLOSED;
            if (!authorStatus || (!isPublic(post) && !"DRAFT".equals(post.getStatus()))) {
                throw new BusinessException(ErrorCode.POST_002_PERMISSION_DENIED,
                        "Only an available post or draft can be published or closed by its author");
            }
            post.setStatus(request.getStatus().name());
        }
        RequestDtos.CreatePostRequest content = new RequestDtos.CreatePostRequest();
        content.setCategory(ApiEnums.PostCategory.valueOf(post.getCategory()));
        content.setTitle(post.getTitle());
        content.setContent(post.getContent());
        validatePostRequest(content);
        contentModerationService.assertPostPublishAllowed(userId, content);
        post.setUpdatedAt(LocalDateTime.now());

        if (postMapper.updateContent(post, expectedStatus) != 1) {
            throw new BusinessException(ErrorCode.COMMON_003_CONFLICT,
                    "Post status changed before update");
        }
        return postMapper.selectDetailById(postId);
    }

    @Override
    public void deletePost(Long userId, Long postId) {
        Post post = requirePostExists(postId);
        if (!userId.equals(post.getAuthorId())) {
            throw new BusinessException(ErrorCode.POST_002_PERMISSION_DENIED);
        }
        postMapper.markDeletedById(postId);
    }

    private void bindAttachments(
            Long userId, Long postId, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        if (attachmentIds.size() > MAX_ATTACHMENT_COUNT) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "attachmentIds exceeds limit");
        }

        Set<Long> uniqueIds = new HashSet<>(attachmentIds);
        if (uniqueIds.size() != attachmentIds.size() || uniqueIds.contains(null)) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "attachmentIds must be unique and non-null");
        }

        for (Long attachmentId : attachmentIds) {
            Attachment attachment = attachmentMapper.selectById(attachmentId);
            validateAttachmentOwnership(attachment, userId);
            if (attachmentMapper.bindTemporaryToOwner(attachmentId, userId, ATTACHMENT_OWNER_TYPE, postId) != 1) {
                throw new BusinessException(
                        ErrorCode.COMMON_999_INTERNAL_ERROR,
                        "Failed to bind post attachment");
            }
        }
    }

    private void validateAttachmentOwnership(Attachment attachment, Long userId) {
        if (attachment == null
                || !userId.equals(attachment.getUploaderId())
                || !ATTACHMENT_OWNER_TYPE.equals(attachment.getOwnerType())
                || attachment.getOwnerId() != null
                || !ApiEnums.AttachmentStatus.TEMPORARY.name().equals(attachment.getStatus())) {
            throw new BusinessException(
                    ErrorCode.FILE_004_INVALID_ATTACHMENT,
                    "Post attachment is unavailable");
        }
    }

    private boolean isPublic(Post post) {
        return "PUBLISHED".equals(post.getStatus()) || "CLOSED".equals(post.getStatus());
    }

    private void validatePostRequest(RequestDtos.CreatePostRequest request) {
        if (request == null || request.getCategory() == null
                || request.getTitle() == null || request.getTitle().isBlank()
                || request.getTitle().length() > 100
                || request.getContent() == null || request.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "Post category, title and content are required; title limit is 100");
        }
    }

    private Post requirePostExists(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null || "DELETED".equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.POST_001_NOT_FOUND);
        }
        return post;
    }
}
