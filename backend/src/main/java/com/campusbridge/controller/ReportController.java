package com.campusbridge.controller;

import com.campusbridge.enums.ReportStatus;
import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.request.CreateReportRequest;
import com.campusbridge.entity.Report;
import com.campusbridge.service.ReportService;
import com.campusbridge.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Report> createReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateReportRequest request) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(reportService.createReport(currentUserId, request));
    }

    @GetMapping
    public ApiResponse<PagedResult<Report>> listReports(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(reportService.listReports(currentUserId, status, page, size));
    }
}
