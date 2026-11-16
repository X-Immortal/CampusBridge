package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.dto.view.DashboardDistributionItem;
import com.campusbridge.dto.view.DashboardSummary;
import com.campusbridge.dto.view.DashboardTodo;
import com.campusbridge.dto.view.DashboardTrend;
import com.campusbridge.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 30;
    private static final int DEFAULT_DAYS = 7;

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummary> summary() {
        return ApiResponse.success(adminDashboardService.getSummary());
    }

    @GetMapping("/trends")
    public ApiResponse<DashboardTrend> trends(@RequestParam(defaultValue = "7") Integer days) {
        return ApiResponse.success(adminDashboardService.getTrends(normalizeDays(days)));
    }

    @GetMapping("/task-status")
    public ApiResponse<List<DashboardDistributionItem>> taskStatus() {
        return ApiResponse.success(adminDashboardService.getTaskStatusStats());
    }

    @GetMapping("/report-status")
    public ApiResponse<List<DashboardDistributionItem>> reportStatus() {
        return ApiResponse.success(adminDashboardService.getReportStatusStats());
    }

    @GetMapping("/report-target-type")
    public ApiResponse<List<DashboardDistributionItem>> reportTargetType() {
        return ApiResponse.success(adminDashboardService.getReportTargetTypeStats());
    }

    @GetMapping("/todos")
    public ApiResponse<List<DashboardTodo>> todos() {
        return ApiResponse.success(adminDashboardService.getTodos());
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
