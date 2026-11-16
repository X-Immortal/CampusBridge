package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.common.GuestTaskMasker;
import com.campusbridge.dto.query.TaskQuery;
import com.campusbridge.dto.request.CreateTaskRequest;
import com.campusbridge.dto.request.ReasonRequest;
import com.campusbridge.dto.request.SubmitCompletionRequest;
import com.campusbridge.dto.request.TaskStatusTransitionRequest;
import com.campusbridge.dto.request.UpdateTaskRequest;
import com.campusbridge.dto.view.TaskDetailView;
import com.campusbridge.dto.view.TaskPageView;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.entity.CompletionProof;
import com.campusbridge.service.CompletionService;
import com.campusbridge.service.TaskDetailService;
import com.campusbridge.service.TaskService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final CurrentUserResolver currentUserResolver;
    private final TaskService taskService;
    private final TaskDetailService taskDetailService;
    private final CompletionService completionService;

    public TaskController(
            CurrentUserResolver currentUserResolver,
            TaskService taskService,
            TaskDetailService taskDetailService,
            CompletionService completionService) {
        this.currentUserResolver = currentUserResolver;
        this.taskService = taskService;
        this.taskDetailService = taskDetailService;
        this.completionService = completionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskView> createTask(
            HttpServletRequest servletRequest,
            @RequestBody CreateTaskRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(taskService.createTask(currentUserId, request));
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskView> saveDraft(
            HttpServletRequest servletRequest,
            @RequestBody CreateTaskRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(taskService.saveDraft(currentUserId, request));
    }

    @GetMapping
    public ApiResponse<TaskPageView> listTasks(
            HttpServletRequest servletRequest,
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
        TaskQuery query = TaskQuery.builder()
                .keyword(keyword)
                .category(category)
                .campus(campus)
                .status(status)
                .rewardType(rewardType)
                .requiresProof(requiresProof)
                .excludeExpired(excludeExpired)
                .sortBy(sortBy)
                .page(page)
                .size(size)
                .build();
        TaskPageView result = taskService.listTasks(query);
        Long currentUserId = currentUserResolver.resolveOptionalUserId(servletRequest);
        result.getTasks().forEach(task -> GuestTaskMasker.maskForGuest(task, currentUserId));
        return ApiResponse.success(result);
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskDetailView> getTaskDetail(HttpServletRequest servletRequest, @PathVariable Long taskId) {
        Long currentUserId = currentUserResolver.resolveOptionalUserId(servletRequest);
        var detail = taskDetailService.getTaskDetail(taskId, currentUserId);
        detail.setTask(GuestTaskMasker.maskForGuest(detail.getTask(), currentUserId));
        return ApiResponse.success(detail);
    }

    @PutMapping("/{taskId}")
    public ApiResponse<TaskView> updateTask(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody UpdateTaskRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(taskService.updateTask(currentUserId, taskId, request));
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<TaskView> cancelTask(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestParam(required = false) String reason) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(taskService.cancelTask(currentUserId, taskId, reason));
    }

    @PostMapping("/{taskId}/provider-withdrawal")
    public ApiResponse<TaskView> withdrawProvider(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody ReasonRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(
                taskService.withdrawFromTask(currentUserId, taskId, request.reason()));
    }

    @DeleteMapping("/{taskId}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteDraft(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        taskService.deleteDraft(currentUserId, taskId);
        return ApiResponse.success();
    }

    @PostMapping("/{taskId}/resend")
    public ApiResponse<TaskView> resendTask(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(taskService.resendTask(currentUserId, taskId));
    }

    @PostMapping("/{taskId}/status-transitions")
    public ApiResponse<TaskView> transitionTaskStatus(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody TaskStatusTransitionRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(taskService.transitionTaskStatus(currentUserId, taskId, request));
    }

    @PostMapping("/{taskId}/completion")
    public ApiResponse<CompletionProof> submitCompletion(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody SubmitCompletionRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(completionService.submitCompletionProof(currentUserId, taskId, request));
    }

    @PostMapping("/{taskId}/completion/confirm")
    public ApiResponse<Void> confirmCompletion(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        completionService.confirmCompletion(currentUserId, taskId);
        return ApiResponse.success();
    }

    @PostMapping("/{taskId}/completion/reject")
    public ApiResponse<Void> rejectCompletion(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody ReasonRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        String reason = request.reason();
        completionService.rejectCompletion(currentUserId, taskId, reason);
        return ApiResponse.success();
    }

    @PostMapping("/{taskId}/completion/dispute")
    public ApiResponse<Void> disputeCompletion(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody ReasonRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        String reason = request.reason();
        completionService.disputeCompletion(currentUserId, taskId, reason);
        return ApiResponse.success();
    }
}
