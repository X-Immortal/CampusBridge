package com.campusbridge.controller;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.Task;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public TaskController(
            CurrentUserResolver currentUserResolver,
            TaskService taskService) {
        this.currentUserResolver = currentUserResolver;
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result createTask(
            HttpServletRequest servletRequest,
            @RequestBody RequestDtos.CreateTaskRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(taskService.createTask(currentUserId, request));
    }

    @GetMapping
    public Result listTasks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String rewardType,
            @RequestParam(required = false) Boolean requiresProof,
            @RequestParam(required = false, defaultValue = "CREATED_AT_DESC") String sortBy,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        List<Task> tasks = taskService.listTasks(keyword, category, campus, status, rewardType, requiresProof, sortBy, page, size);
        Map<String, Object> response = new HashMap<>();
        response.put("tasks", tasks);
        response.put("page", page);
        response.put("size", size);
        return Result.success(response);
    }

    @GetMapping("/{taskId}")
    public Result getTaskDetail(@PathVariable Long taskId) {
        return Result.success(taskService.getTaskDetail(taskId));
    }

    @PatchMapping("/{taskId}")
    public Result updateTask(
            @PathVariable Long taskId,
            @RequestBody RequestDtos.UpdateTaskRequest request) {
        return Result.success();
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result cancelTask(@PathVariable Long taskId) {
        return Result.success();
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
            @PathVariable Long taskId,
            @RequestBody RequestDtos.SubmitCompletionRequest request) {
        return Result.success();
    }
}
