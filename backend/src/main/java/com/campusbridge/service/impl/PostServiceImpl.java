package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.dto.view.ViewDtos;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.PostInteractionMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.pojo.Attachment;
import com.campusbridge.pojo.Post;
import com.campusbridge.pojo.PostInteraction;
import com.campusbridge.pojo.Reply;
import com.campusbridge.service.ContentModerationService;
import com.campusbridge.service.PostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public Post createPost(Long userId, RequestDtos.CreatePostRequest request) {
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
        return post;
    }

    @Override
    public ResponseDtos.PagedResult<Post> listPosts(
            String keyword, String category, String status, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int offset = (safePage - 1) * safeSize;

        List<Post> posts = postMapper.selectWithFilters(category, status, keyword, offset, safeSize);
        long total = postMapper.countWithFilters(category, status, keyword);
        return new ResponseDtos.PagedResult<>(posts, safePage, safeSize, total);
    }

    @Override
    public ViewDtos.PostDetailView getPostDetail(Long postId, Long currentUserId) {
        Post post = requirePostExists(postId);
        List<Reply> replies = replyMapper.selectByPostId(postId, 0, 50);
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
    public Post updatePost(Long userId, Long postId, RequestDtos.UpdatePostRequest request) {
        Post post = requirePostExists(postId);
        if (!userId.equals(post.getAuthorId())) {
            throw new BusinessException(ErrorCode.POST_002_PERMISSION_DENIED);
        }

        if (request.getTitle() != null) {
            post.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            post.setContent(request.getContent());
        }
        if (request.getStatus() != null) {
            post.setStatus(request.getStatus().name());
        }
        post.setUpdatedAt(LocalDateTime.now());

        if (postMapper.updateById(post) != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to update post");
        }
        return post;
    }

    @Override
    public void deletePost(Long userId, Long postId) {
        Post post = requirePostExists(postId);
        if (!userId.equals(post.getAuthorId())) {
            throw new BusinessException(ErrorCode.POST_002_PERMISSION_DENIED);
        }
        postMapper.deleteById(postId);
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
            attachment.setOwnerType(ATTACHMENT_OWNER_TYPE);
            attachment.setOwnerId(postId);
            attachment.setStatus("ACTIVE");
            if (attachmentMapper.updateById(attachment) != 1) {
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

    private Post requirePostExists(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_001_NOT_FOUND);
        }
        return post;
    }
}
