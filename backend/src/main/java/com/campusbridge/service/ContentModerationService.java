package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;

public interface ContentModerationService {
    void assertPostPublishAllowed(
            Long operatorId,
            RequestDtos.CreatePostRequest request);

    void assertTaskPublishAllowed(
            Long operatorId,
            RequestDtos.CreateTaskRequest request);
}
