package com.campusbridge.service;

import com.campusbridge.dto.request.CreatePostInteractionRequest;
import com.campusbridge.entity.PostInteraction;

public interface InteractionService {
    PostInteraction createInteraction(Long userId, Long postId, CreatePostInteractionRequest request);

    void cancelInteraction(Long userId, Long postId, String type);

    PostInteraction acceptReply(Long userId, Long postId, Long replyId);
}
