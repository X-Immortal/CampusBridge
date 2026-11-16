package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.dto.view.MyTaskApplicationView;
import com.campusbridge.entity.TaskApplication;
import com.campusbridge.service.TaskApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/task-applications")
public class MyTaskApplicationController {
    private final CurrentUserResolver currentUserResolver;
    private final TaskApplicationService taskApplicationService;

    public MyTaskApplicationController(
            CurrentUserResolver currentUserResolver,
            TaskApplicationService taskApplicationService) {
        this.currentUserResolver = currentUserResolver;
        this.taskApplicationService = taskApplicationService;
    }

    @GetMapping("/mine")
    public ApiResponse<List<MyTaskApplicationView>> listMyApplications(HttpServletRequest servletRequest) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(taskApplicationService.listMyApplications(currentUserId));
    }

    @DeleteMapping("/{applicationId}")
    public ApiResponse<TaskApplication> withdrawApplication(
            HttpServletRequest servletRequest,
            @PathVariable Long applicationId) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(
                taskApplicationService.withdrawApplication(currentUserId, applicationId));
    }
}
