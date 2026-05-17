package com.campusbridge.controller;

import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.ModerationRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminModerationController {

    @GetMapping("/moderation/tasks/pending")
    public Result listPendingTasks() {
        return Result.success();
    }

    @PostMapping("/moderation/tasks/{taskId}/approve")
    public Result approveTask(
            @PathVariable Long taskId,
            @RequestBody(required = false) ModerationRecord request) {
        return Result.success();
    }

    @PostMapping("/moderation/tasks/{taskId}/reject")
    public Result rejectTask(
            @PathVariable Long taskId,
            @RequestBody(required = false) ModerationRecord request) {
        return Result.success();
    }

    @PostMapping("/reports/{reportId}/resolve")
    public Result resolveReport(
            @PathVariable Long reportId,
            @RequestBody(required = false) ModerationRecord request) {
        return Result.success();
    }
}
