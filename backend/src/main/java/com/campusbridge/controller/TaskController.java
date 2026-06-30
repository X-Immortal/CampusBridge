package com.campusbridge.controller;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.Task;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.service.TaskService;
import com.campusbridge.service.CompletionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final CurrentUserResolver currentUserResolver;
    private final TaskService taskService;
    private final CompletionService completionService;

    public TaskController(
            CurrentUserResolver currentUserResolver,
            TaskService taskService,
            CompletionService completionService) {
        this.currentUserResolver = currentUserResolver;
        this.taskService = taskService;
        this.completionService = completionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result createTask(
            HttpServletRequest servletRequest,
            @RequestBody RequestDtos.CreateTaskRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(taskService.createTask(currentUserId, request));
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public Result saveDraft(
            HttpServletRequest servletRequest,
            @RequestBody RequestDtos.CreateTaskRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(taskService.saveDraft(currentUserId, request));
    }

    @GetMapping
    public Result listTasks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String rewardType,
            @RequestParam(required = false) Boolean requiresProof,
            @RequestParam(required = false, defaultValue = "false") Boolean excludeExpired,
            @RequestParam(required = false, defaultValue = "CREATED_AT_DESC") String sortBy,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Map<String, Object> result = taskService.listTasks(
                keyword,
                category,
                campus,
                status,
                rewardType,
                requiresProof,
                excludeExpired,
                sortBy,
                page,
                size);
        return Result.success(result);
    }

    @GetMapping("/{taskId}")
    public Result getTaskDetail(@PathVariable Long taskId) {
        return Result.success(taskService.getTaskDetail(taskId));
    }

    @PutMapping("/{taskId}")
    public Result updateTask(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody RequestDtos.UpdateTaskRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(taskService.updateTask(currentUserId, taskId, request));
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result cancelTask(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestParam(required = false) String reason) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(taskService.cancelTask(currentUserId, taskId, reason));
    }

    @DeleteMapping("/{taskId}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result deleteDraft(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        taskService.deleteDraft(currentUserId, taskId);
        return Result.success();
    }

    @PostMapping("/{taskId}/resend")
    public Result resendTask(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(taskService.resendTask(currentUserId, taskId));
    }

    @PostMapping("/{taskId}/status-transitions")
    public Result transitionTaskStatus(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody RequestDtos.TaskStatusTransitionRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(taskService.transitionTaskStatus(currentUserId, taskId, request));
    }

    @PostMapping("/{taskId}/completion")
    public Result submitCompletion(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody RequestDtos.SubmitCompletionRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(completionService.submitCompletionProof(currentUserId, taskId, request));
    }

    @PostMapping("/{taskId}/completion/confirm")
    public Result confirmCompletion(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        completionService.confirmCompletion(currentUserId, taskId);
        return Result.success();
    }

    @PostMapping("/{taskId}/completion/reject")
    public Result rejectCompletion(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody java.util.Map<String, String> request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        String reason = request.get("reason");
        completionService.rejectCompletion(currentUserId, taskId, reason);
        return Result.success();
    }

    @PostMapping("/{taskId}/completion/dispute")
    public Result disputeCompletion(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody java.util.Map<String, String> request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        String reason = request.get("reason");
        completionService.disputeCompletion(currentUserId, taskId, reason);
        return Result.success();
    }
}
