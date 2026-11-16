package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.dto.request.CreateTaskApplicationRequest;
import com.campusbridge.dto.view.TaskApplicationCandidateView;
import com.campusbridge.entity.TaskApplication;
import com.campusbridge.service.TaskApplicationService;
import com.campusbridge.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}/applications")
@RequiredArgsConstructor
public class TaskApplicationController {
    private final TaskApplicationService taskApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskApplication> createApplication(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId,
            @Valid @RequestBody CreateTaskApplicationRequest request) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(taskApplicationService.createApplication(currentUserId, taskId, request));
    }

    @GetMapping
    public ApiResponse<List<TaskApplicationCandidateView>> listApplications(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(taskApplicationService.listApplications(currentUserId, taskId));
    }

    @PostMapping("/{applicationId}/accept")
    public ApiResponse<TaskApplication> acceptApplication(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId,
            @PathVariable Long applicationId) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(
                taskApplicationService.acceptApplication(currentUserId, taskId, applicationId));
    }
}
