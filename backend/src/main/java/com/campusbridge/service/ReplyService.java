package com.campusbridge.service;

import com.campusbridge.dto.request.CreateReplyRequest;
import com.campusbridge.dto.view.ReplyView;

public interface ReplyService {
    ReplyView createReply(Long userId, Long postId, CreateReplyRequest request);
}
