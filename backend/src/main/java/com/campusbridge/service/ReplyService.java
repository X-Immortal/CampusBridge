package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Reply;

public interface ReplyService {
    Reply createReply(Long userId, Long postId, RequestDtos.CreateReplyRequest request);
}
