package com.campusbridge.service.impl;

import com.campusbridge.enums.ReplyStatus;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.CreateReplyRequest;
import com.campusbridge.dto.view.ReplyView;
import com.campusbridge.entity.Post;
import com.campusbridge.entity.Reply;
import com.campusbridge.enums.PostStatus;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.service.ReplyNotificationService;
import com.campusbridge.service.ReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
@Service
@RequiredArgsConstructor
public class ReplyServiceImpl implements ReplyService {
    private final ReplyMapper replyMapper;
    private final PostMapper postMapper;
    private final ReplyNotificationService notificationService;

    @Override
    public ReplyView createReply(Long userId, Long postId, CreateReplyRequest request) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getStatus() != PostStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.POST_001_NOT_FOUND);
        }

        Reply parentReply = null;
        if (request.getParentReplyId() != null) {
            parentReply = replyMapper.selectById(request.getParentReplyId());
            if (parentReply == null || !postId.equals(parentReply.getPostId())
                    || parentReply.getStatus() != ReplyStatus.PUBLISHED) {
                throw new BusinessException(
                        ErrorCode.COMMON_001_VALIDATION_ERROR,
                        "parent reply does not belong to this post");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Reply reply = new Reply();
        reply.setPostId(postId);
        reply.setAuthorId(userId);
        reply.setParentReplyId(request.getParentReplyId());
        reply.setContent(request.getContent());
        reply.setStatus(ReplyStatus.PUBLISHED);
        reply.setLikeCount(0);
        reply.setCreatedAt(now);

        // Lock the parent before the child insert acquires its foreign-key shared lock.
        if (postMapper.incrementReplyCount(postId) != 1) {
            throw new BusinessException(ErrorCode.COMMON_003_CONFLICT, "Post no longer accepts replies");
        }

        if (replyMapper.insert(reply) != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create reply");
        }

        notificationService.notifyReplyCreated(post, reply, parentReply);

        return ReplyView.from(reply);
    }
}
