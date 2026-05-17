package com.campusbridge.controller;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Result;
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

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result createTask(@RequestBody RequestDtos.CreateTaskRequest request) {
        return Result.success();
    }

    @GetMapping
    public Result listTasks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String rewardType,
            @RequestParam(required = false) Boolean requiresProof,
            @RequestParam(required = false, defaultValue = "CREATED_AT") String sortBy,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success();
    }

    @GetMapping("/{taskId}")
    public Result getTaskDetail(@PathVariable Long taskId) {
        return Result.success();
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
            @PathVariable Long taskId,
            @RequestBody RequestDtos.TaskStatusTransitionRequest request) {
        return Result.success();
    }

    @PostMapping("/{taskId}/completion")
    public Result submitCompletion(
            @PathVariable Long taskId,
            @RequestBody RequestDtos.SubmitCompletionRequest request) {
        return Result.success();
    }
}
