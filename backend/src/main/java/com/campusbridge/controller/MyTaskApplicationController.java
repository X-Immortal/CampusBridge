package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.dto.view.MyTaskApplicationView;
import com.campusbridge.entity.TaskApplication;
import com.campusbridge.service.TaskApplicationService;
import com.campusbridge.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/task-applications")
@RequiredArgsConstructor
public class MyTaskApplicationController {
    private final TaskApplicationService taskApplicationService;

    @GetMapping("/mine")
    public ApiResponse<List<MyTaskApplicationView>> listMyApplications(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(taskApplicationService.listMyApplications(currentUserId));
    }

    @DeleteMapping("/{applicationId}")
    public ApiResponse<TaskApplication> withdrawApplication(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long applicationId) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(
                taskApplicationService.withdrawApplication(currentUserId, applicationId));
    }
}
