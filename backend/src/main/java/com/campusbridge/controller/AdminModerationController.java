package com.campusbridge.controller;

import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.TaskModerationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminModerationController {

    private final CurrentUserResolver currentUserResolver;
    private final TaskModerationService taskModerationService;

    public AdminModerationController(
            CurrentUserResolver currentUserResolver,
            TaskModerationService taskModerationService) {
        this.currentUserResolver = currentUserResolver;
        this.taskModerationService = taskModerationService;
    }

    @GetMapping("/moderation/tasks/pending")
    public Result listPendingTasks(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        List<Task> tasks = taskModerationService.listPendingReviewTasks(page, size);
        int total = taskModerationService.countPendingReviewTasks();

        Map<String, Object> response = new HashMap<>();
        response.put("tasks", tasks);
        response.put("total", total);
        response.put("page", page);
        response.put("size", size);
        return Result.success(response);
    }

    @PostMapping("/moderation/tasks/{taskId}/approve")
    @ResponseStatus(HttpStatus.OK)
    public Result approveTask(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody(required = false) Map<String, String> request) {
        Long adminId = currentUserResolver.requireCurrentUserId(servletRequest);
        String reviewNote = request != null ? request.get("reviewNote") : null;

        Task task = taskModerationService.approveTask(taskId, adminId, reviewNote);
        return Result.success(task);
    }

    @PostMapping("/moderation/tasks/{taskId}/reject")
    @ResponseStatus(HttpStatus.OK)
    public Result rejectTask(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> request) {
        Long adminId = currentUserResolver.requireCurrentUserId(servletRequest);
        String rejectReason = request.get("rejectReason");

        Task task = taskModerationService.rejectTask(taskId, adminId, rejectReason);
        return Result.success(task);
    }

    @PostMapping("/reports/{reportId}/resolve")
    public Result resolveReport(
            @PathVariable Long reportId,
            @RequestBody(required = false) Map<String, Object> request) {
        // TODO: Implement report resolution logic in BE16
        return Result.success();
    }
}
