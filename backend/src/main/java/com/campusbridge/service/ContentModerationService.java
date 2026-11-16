package com.campusbridge.service;

import com.campusbridge.dto.request.CreatePostRequest;
import com.campusbridge.dto.request.CreateTaskRequest;
import com.campusbridge.dto.request.UpdatePostRequest;
import com.campusbridge.dto.request.UpdateTaskRequest;

public interface ContentModerationService {
    void assertPostPublishAllowed(
            Long operatorId,
            CreatePostRequest request);

    void assertPostUpdateAllowed(Long operatorId, UpdatePostRequest request);

    void assertTaskPublishAllowed(
            Long operatorId,
            CreateTaskRequest request);

    void assertTaskUpdateAllowed(
            Long operatorId,
            UpdateTaskRequest request,
            String sensitiveReason);
}
