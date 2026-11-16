package com.campusbridge.service;

import com.campusbridge.dto.request.CreatePostInteractionRequest;
import com.campusbridge.dto.view.AcceptedReplyView;
import com.campusbridge.entity.PostInteraction;
import com.campusbridge.enums.PostInteractionType;

public interface InteractionService {
    PostInteraction createInteraction(Long userId, Long postId, CreatePostInteractionRequest request);

    void cancelInteraction(Long userId, Long postId, PostInteractionType type);

    AcceptedReplyView acceptReply(Long userId, Long postId, Long replyId);
}
