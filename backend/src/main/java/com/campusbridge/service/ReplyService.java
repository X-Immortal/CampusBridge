package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.view.ReplyView;

public interface ReplyService {
    ReplyView createReply(Long userId, Long postId, RequestDtos.CreateReplyRequest request);
}
