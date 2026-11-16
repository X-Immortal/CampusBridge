package com.campusbridge.controller;

import com.campusbridge.enums.TaskSort;
import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.GuestTaskMasker;
import com.campusbridge.dto.query.TaskQuery;
import com.campusbridge.dto.request.CompletionReasonRequest;
import com.campusbridge.dto.request.CreateTaskRequest;
import com.campusbridge.dto.request.SaveTaskDraftRequest;
import com.campusbridge.dto.request.SubmitCompletionRequest;
import com.campusbridge.dto.request.TaskReasonRequest;
import com.campusbridge.dto.request.TaskStatusTransitionRequest;
import com.campusbridge.dto.request.UpdateTaskRequest;
import com.campusbridge.dto.view.TaskDetailView;
import com.campusbridge.dto.view.TaskPageView;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.entity.CompletionProof;
import com.campusbridge.enums.RewardType;
import com.campusbridge.enums.TaskCategory;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.service.CompletionService;
import com.campusbridge.service.TaskDetailService;
import com.campusbridge.service.TaskService;
import com.campusbridge.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;
    private final TaskDetailService taskDetailService;
    private final CompletionService completionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskView> createTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateTaskRequest request) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(taskService.createTask(currentUserId, request));
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskView> saveDraft(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SaveTaskDraftRequest request) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(taskService.saveDraft(currentUserId, request));
    }

    @GetMapping
    public ApiResponse<TaskPageView> listTasks(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) TaskCategory category,
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) RewardType rewardType,
            @RequestParam(required = false) Boolean requiresProof,
            @RequestParam(required = false, defaultValue = "false") Boolean excludeExpired,
            @RequestParam(required = false, defaultValue = "CREATED_AT_DESC") TaskSort sortBy,
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
        Long currentUserId = principal == null ? null : principal.getUserId();
        result.getTasks().forEach(task -> GuestTaskMasker.maskForGuest(task, currentUserId));
        return ApiResponse.success(result);
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskDetailView> getTaskDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId) {
        Long currentUserId = principal == null ? null : principal.getUserId();
        var detail = taskDetailService.getTaskDetail(taskId, currentUserId);
        detail.setTask(GuestTaskMasker.maskForGuest(detail.getTask(), currentUserId));
        return ApiResponse.success(detail);
    }

    @PutMapping("/{taskId}")
    public ApiResponse<TaskView> updateTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskRequest request) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(taskService.updateTask(currentUserId, taskId, request));
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<TaskView> cancelTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId,
            @Size(max = 300, message = "reason is too long") @RequestParam(required = false) String reason) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(taskService.cancelTask(currentUserId, taskId, reason));
    }

    @PostMapping("/{taskId}/provider-withdrawal")
    public ApiResponse<TaskView> withdrawProvider(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskReasonRequest request) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(
                taskService.withdrawFromTask(currentUserId, taskId, request.reason()));
    }

    @DeleteMapping("/{taskId}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteDraft(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId) {
        Long currentUserId = principal.getUserId();
        taskService.deleteDraft(currentUserId, taskId);
        return ApiResponse.success();
    }

    @PostMapping("/{taskId}/resend")
    public ApiResponse<TaskView> resendTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId,
            @Valid @RequestBody CreateTaskRequest request) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(taskService.resendTask(currentUserId, taskId, request));
    }

    @PostMapping("/{taskId}/status-transitions")
    public ApiResponse<TaskView> transitionTaskStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskStatusTransitionRequest request) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(taskService.transitionTaskStatus(currentUserId, taskId, request));
    }

    @PostMapping("/{taskId}/completion")
    public ApiResponse<CompletionProof> submitCompletion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId,
            @Valid @RequestBody SubmitCompletionRequest request) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(completionService.submitCompletionProof(currentUserId, taskId, request));
    }

    @PostMapping("/{taskId}/completion/confirm")
    public ApiResponse<Void> confirmCompletion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId) {
        Long currentUserId = principal.getUserId();
        completionService.confirmCompletion(currentUserId, taskId);
        return ApiResponse.success();
    }

    @PostMapping("/{taskId}/completion/reject")
    public ApiResponse<Void> rejectCompletion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId,
            @Valid @RequestBody CompletionReasonRequest request) {
        Long currentUserId = principal.getUserId();
        String reason = request.reason();
        completionService.rejectCompletion(currentUserId, taskId, reason);
        return ApiResponse.success();
    }

    @PostMapping("/{taskId}/completion/dispute")
    public ApiResponse<Void> disputeCompletion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId,
            @Valid @RequestBody CompletionReasonRequest request) {
        Long currentUserId = principal.getUserId();
        String reason = request.reason();
        completionService.disputeCompletion(currentUserId, taskId, reason);
        return ApiResponse.success();
    }
}
