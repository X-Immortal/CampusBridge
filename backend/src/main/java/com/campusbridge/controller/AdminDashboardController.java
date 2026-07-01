package com.campusbridge.controller;

import com.campusbridge.pojo.Result;
import com.campusbridge.service.AdminDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 30;
    private static final int DEFAULT_DAYS = 7;

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/summary")
    public Result summary() {
        return Result.success(adminDashboardService.getSummary());
    }

    @GetMapping("/trends")
    public Result trends(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(adminDashboardService.getTrends(normalizeDays(days)));
    }

    @GetMapping("/task-status")
    public Result taskStatus() {
        return Result.success(adminDashboardService.getTaskStatusStats());
    }

    @GetMapping("/report-status")
    public Result reportStatus() {
        return Result.success(adminDashboardService.getReportStatusStats());
    }

    @GetMapping("/report-target-type")
    public Result reportTargetType() {
        return Result.success(adminDashboardService.getReportTargetTypeStats());
    }

    @GetMapping("/todos")
    public Result todos() {
        return Result.success(adminDashboardService.getTodos());
    }

    private int normalizeDays(Integer days) {
        if (days == null) {
            return DEFAULT_DAYS;
        }
        if (days < MIN_DAYS) {
            return MIN_DAYS;
        }
        if (days > MAX_DAYS) {
            return MAX_DAYS;
        }
        return days;
    }
}
