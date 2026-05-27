package com.campusbridge.controller;

import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Result;
import com.campusbridge.service.TaskApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}/applications")
public class TaskApplicationController {
    private final CurrentUserResolver currentUserResolver;
    private final TaskApplicationService taskApplicationService;

    public TaskApplicationController(
            CurrentUserResolver currentUserResolver,
            TaskApplicationService taskApplicationService) {
        this.currentUserResolver = currentUserResolver;
        this.taskApplicationService = taskApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result createApplication(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody RequestDtos.CreateTaskApplicationRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(taskApplicationService.createApplication(currentUserId, taskId, request));
    }

    @GetMapping
    public Result listApplications(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(taskApplicationService.listApplications(currentUserId, taskId));
    }

    @PostMapping("/{applicationId}/accept")
    public Result acceptApplication(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @PathVariable Long applicationId) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(
                taskApplicationService.acceptApplication(currentUserId, taskId, applicationId));
    }
}
