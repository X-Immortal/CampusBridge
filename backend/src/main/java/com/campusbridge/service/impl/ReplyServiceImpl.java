package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.pojo.Post;
import com.campusbridge.pojo.Reply;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.ReplyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
@Service
public class ReplyServiceImpl implements ReplyService {
    private final ReplyMapper replyMapper;
    private final PostMapper postMapper;
    private final NotificationService notificationService;

    public ReplyServiceImpl(ReplyMapper replyMapper, PostMapper postMapper,
                            NotificationService notificationService) {
        this.replyMapper = replyMapper;
        this.postMapper = postMapper;
        this.notificationService = notificationService;
    }

    @Override
    public Reply createReply(Long userId, Long postId, RequestDtos.CreateReplyRequest request) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_001_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        Reply reply = new Reply();
        reply.setPostId(postId);
        reply.setAuthorId(userId);
        reply.setParentReplyId(request.getParentReplyId());
        reply.setContent(request.getContent());
        reply.setStatus("PUBLISHED");
        reply.setLikeCount(0);
        reply.setCreatedAt(now);

        if (replyMapper.insert(reply) != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create reply");
        }

        post.setReplyCount((post.getReplyCount() == null ? 0 : post.getReplyCount()) + 1);
        postMapper.updateById(post);

        notificationService.notifyPostReplied(post, reply, userId);

        return reply;
    }
}
