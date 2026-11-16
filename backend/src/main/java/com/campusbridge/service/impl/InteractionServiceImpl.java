package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.CreatePostInteractionRequest;
import com.campusbridge.entity.Post;
import com.campusbridge.entity.PostInteraction;
import com.campusbridge.entity.Reply;
import com.campusbridge.enums.PostInteractionType;
import com.campusbridge.mapper.PostInteractionMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.service.InteractionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
@Service
public class InteractionServiceImpl implements InteractionService {
    private static final String TARGET_TYPE_POST = "POST";

    private final PostInteractionMapper interactionMapper;
    private final PostMapper postMapper;
    private final ReplyMapper replyMapper;

    public InteractionServiceImpl(
            PostInteractionMapper interactionMapper,
            PostMapper postMapper,
            ReplyMapper replyMapper) {
        this.interactionMapper = interactionMapper;
        this.postMapper = postMapper;
        this.replyMapper = replyMapper;
    }

    @Override
    public PostInteraction createInteraction(
            Long userId, Long postId, CreatePostInteractionRequest request) {
        if (request == null || request.getType() == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "Interaction type is required");
        }
        String type = request.getType().name();

        Post post = requirePostExists(postId);

        PostInteraction existing = interactionMapper.selectByUserTargetType(
                userId, TARGET_TYPE_POST, postId, type);
        if (existing != null) {
            throw new BusinessException(ErrorCode.COMMON_003_CONFLICT,
                    "Already interacted with this post");
        }

        PostInteraction interaction = new PostInteraction();
        interaction.setUserId(userId);
        interaction.setTargetType(TARGET_TYPE_POST);
        interaction.setTargetId(postId);
        interaction.setType(type);
        interaction.setCreatedAt(LocalDateTime.now());

        try {
            if (interactionMapper.insert(interaction) != 1) {
                throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "Failed to create interaction");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.COMMON_003_CONFLICT, "Already interacted with this post");
        }

        updatePostCount(post, type, 1);
        return interaction;
    }

    @Override
    public void cancelInteraction(Long userId, Long postId, String type) {
        if (!PostInteractionType.LIKE.name().equals(type)
                && !PostInteractionType.MARK.name().equals(type)) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "Interaction type must be LIKE or MARK");
        }
        requirePostExists(postId);

        int deleted = interactionMapper.deleteByUserTargetType(
                userId, TARGET_TYPE_POST, postId, type);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "Interaction not found");
        }

        Post post = postMapper.selectById(postId);
        if (post != null) {
            updatePostCount(post, type, -1);
        }
    }

    @Override
    public PostInteraction acceptReply(Long userId, Long postId, Long replyId) {
        Post post = requirePostExists(postId);
        if (!userId.equals(post.getAuthorId())) {
            throw new BusinessException(ErrorCode.POST_002_PERMISSION_DENIED);
        }

        Reply reply = replyMapper.selectById(replyId);
        if (reply == null || !"PUBLISHED".equals(reply.getStatus())) {
            throw new BusinessException(ErrorCode.POST_003_REPLY_NOT_FOUND);
        }
        if (!reply.getPostId().equals(postId)) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "Reply does not belong to this post");
        }

        if (postMapper.updateAcceptedReply(postId, replyId) != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to accept reply");
        }

        PostInteraction interaction = new PostInteraction();
        interaction.setUserId(userId);
        interaction.setTargetType(TARGET_TYPE_POST);
        interaction.setTargetId(postId);
        interaction.setType("ACCEPT_REPLY");
        interaction.setCreatedAt(LocalDateTime.now());
        // Acceptance is stored in posts.accepted_reply_id; interactions persist only LIKE and MARK.
        return interaction;
    }

    private Post requirePostExists(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null || !("PUBLISHED".equals(post.getStatus()) || "CLOSED".equals(post.getStatus()))) {
            throw new BusinessException(ErrorCode.POST_001_NOT_FOUND);
        }
        return post;
    }

    private void updatePostCount(Post post, String type, int delta) {
        if (postMapper.adjustInteractionCount(post.getId(), type, delta) != 1) {
            throw new BusinessException(ErrorCode.COMMON_003_CONFLICT, "Post is no longer available");
        }
    }
}
