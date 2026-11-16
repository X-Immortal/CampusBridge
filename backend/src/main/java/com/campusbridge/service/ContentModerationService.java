package com.campusbridge.service;

import com.campusbridge.dto.request.CreatePostRequest;
import com.campusbridge.dto.request.CreateTaskRequest;

public interface ContentModerationService {
    void assertPostPublishAllowed(
            Long operatorId,
            CreatePostRequest request);

    void assertTaskPublishAllowed(
            Long operatorId,
            CreateTaskRequest request);
}
