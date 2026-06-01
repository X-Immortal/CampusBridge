package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.PostInteractionMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.pojo.Post;
import com.campusbridge.pojo.PostInteraction;
import com.campusbridge.pojo.Reply;
import com.campusbridge.service.InteractionService;
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
            Long userId, Long postId, RequestDtos.CreatePostInteractionRequest request) {
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

        if (interactionMapper.insert(interaction) != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create interaction");
        }

        updatePostCount(post, type, 1);
        return interaction;
    }

    @Override
    public void cancelInteraction(Long userId, Long postId, String type) {
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
        if (reply == null) {
            throw new BusinessException(ErrorCode.POST_003_REPLY_NOT_FOUND);
        }
        if (!reply.getPostId().equals(postId)) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "Reply does not belong to this post");
        }

        post.setAcceptedReplyId(replyId);
        if (postMapper.updateById(post) != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to accept reply");
        }

        PostInteraction interaction = new PostInteraction();
        interaction.setUserId(userId);
        interaction.setTargetType(TARGET_TYPE_POST);
        interaction.setTargetId(postId);
        interaction.setType("ACCEPT_REPLY");
        interaction.setCreatedAt(LocalDateTime.now());
        interactionMapper.insert(interaction);

        return interaction;
    }

    private Post requirePostExists(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_001_NOT_FOUND);
        }
        return post;
    }

    private void updatePostCount(Post post, String type, int delta) {
        if (ApiEnums.PostInteractionType.LIKE.name().equals(type)) {
            int newCount = Math.max(0, (post.getLikeCount() == null ? 0 : post.getLikeCount()) + delta);
            post.setLikeCount(newCount);
        } else if (ApiEnums.PostInteractionType.MARK.name().equals(type)) {
            int newCount = Math.max(0, (post.getMarkCount() == null ? 0 : post.getMarkCount()) + delta);
            post.setMarkCount(newCount);
        } else {
            return;
        }
        postMapper.updateById(post);
    }
}
