package com.campusbridge.service;

import com.campusbridge.entity.Post;
import com.campusbridge.entity.Reply;

public interface ReplyNotificationService {
    void notifyReplyCreated(Post post, Reply reply, Reply parentReply);
}
