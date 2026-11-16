package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.request.CreateReportRequest;
import com.campusbridge.entity.Report;
import com.campusbridge.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final CurrentUserResolver currentUserResolver;
    private final ReportService reportService;

    public ReportController(
            CurrentUserResolver currentUserResolver,
            ReportService reportService) {
        this.currentUserResolver = currentUserResolver;
        this.reportService = reportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Report> createReport(
            HttpServletRequest servletRequest,
            @RequestBody CreateReportRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(reportService.createReport(currentUserId, request));
    }

    @GetMapping
    public ApiResponse<PagedResult<Report>> listReports(
            HttpServletRequest servletRequest,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(reportService.listReports(currentUserId, status, page, size));
    }
}
