package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.PostInteraction;

public interface InteractionService {
    PostInteraction createInteraction(Long userId, Long postId, RequestDtos.CreatePostInteractionRequest request);

    void cancelInteraction(Long userId, Long postId, String type);

    PostInteraction acceptReply(Long userId, Long postId, Long replyId);
}
