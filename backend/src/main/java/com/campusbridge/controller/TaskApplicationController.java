package com.campusbridge.controller;

import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.TaskApplication;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result createApplication(
            @PathVariable Long taskId,
            @RequestBody TaskApplication request) {
        return Result.success();
    }

    @GetMapping
    public Result listApplications(@PathVariable Long taskId) {
        return Result.success();
    }

    @PostMapping("/{applicationId}/accept")
    public Result acceptApplication(
            @PathVariable Long taskId,
            @PathVariable Long applicationId) {
        return Result.success();
    }
}
