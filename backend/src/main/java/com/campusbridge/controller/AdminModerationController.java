package com.campusbridge.controller;

import com.campusbridge.enums.ReportStatus;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.query.TaskQuery;
import com.campusbridge.dto.request.ReasonRequest;
import com.campusbridge.dto.request.ResolveReportRequest;
import com.campusbridge.dto.request.TaskReasonRequest;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.dto.view.VerificationReviewView;
import com.campusbridge.entity.CampusVerification;
import com.campusbridge.entity.ModerationRecord;
import com.campusbridge.entity.Report;
import com.campusbridge.entity.Task;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.service.ReportService;
import com.campusbridge.service.VerificationService;
import com.campusbridge.service.impl.TaskModerationService;
import com.campusbridge.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminModerationController {

    private final ReportService reportService;
    private final VerificationService verificationService;
    private final TaskMapper taskMapper;
    private final TaskModerationService taskModerationService;

    @GetMapping("/moderation/tasks/pending")
    public ApiResponse<List<TaskView>> listPendingTasks(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);

        TaskQuery query = TaskQuery.builder()
                .status(TaskStatus.PENDING_REVIEW)
                .page(safePage)
                .size(safeSize)
                .includePrivate(true)
                .build();

        Page<TaskView> result = taskMapper.selectWithFilters(new Page<>(safePage, safeSize, false), query);
        return ApiResponse.success(result.getRecords());
    }

    @PostMapping("/moderation/tasks/{taskId}/approve")
    public ApiResponse<Task> approveTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId) {

        Long adminUserId = principal.getUserId();
        return ApiResponse.success(taskModerationService.approveTask(adminUserId, taskId));
    }

    @PostMapping("/moderation/tasks/{taskId}/reject")
    public ApiResponse<Task> rejectTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId,
            @Valid @RequestBody(required = false) TaskReasonRequest request) {

        Long adminUserId = principal.getUserId();
        String reason = request == null ? null : request.reason();
        return ApiResponse.success(taskModerationService.rejectTask(adminUserId, taskId, reason));
    }

    @GetMapping("/reports")
    public ApiResponse<PagedResult<Report>> listReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ApiResponse.success(reportService.listReportsForAdmin(status, page, size));
    }

    @PostMapping("/reports/{reportId}/resolve")
    public ApiResponse<ModerationRecord> resolveReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long reportId,
            @Valid @RequestBody ResolveReportRequest request) {
        Long currentUserId = principal.getUserId();
        return ApiResponse.success(reportService.resolveReport(currentUserId, reportId, request));
    }

    // === 认证审核 ===

    @GetMapping("/verifications/pending")
    public ApiResponse<PagedResult<CampusVerification>> listPendingVerifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(verificationService.listPending(page, size));
    }

    @PostMapping("/verifications/{id}/approve")
    public ApiResponse<VerificationReviewView> approveVerification(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        Long adminUserId = principal.getUserId();
        VerificationReviewView result = verificationService.review(adminUserId, id, true, null);
        return ApiResponse.success(result);
    }

    @PostMapping("/verifications/{id}/reject")
    public ApiResponse<VerificationReviewView> rejectVerification(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody ReasonRequest body) {
        Long adminUserId = principal.getUserId();
        String reason = body != null ? body.reason() : "";
        VerificationReviewView result = verificationService.review(adminUserId, id, false, reason);
        return ApiResponse.success(result);
    }

    // === 申诉处理 ===

    @GetMapping("/appeals")
    public ApiResponse<PagedResult<TaskView>> listAppeals(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);

        TaskQuery query = TaskQuery.builder()
                .status(TaskStatus.DISPUTED)
                .page(safePage)
                .size(safeSize)
                .build();

        Page<TaskView> result = taskMapper.selectWithFilters(new Page<>(safePage, safeSize), query);
        return ApiResponse.success(new PagedResult<>(
                result.getRecords(), safePage, safeSize, result.getTotal()));
    }

    @PostMapping("/appeals/{taskId}/dismiss")
    public ApiResponse<Void> dismissAppeal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId) {

        Long adminUserId = principal.getUserId();
        taskModerationService.dismissAppeal(adminUserId, taskId);
        return ApiResponse.success();
    }

    @PostMapping("/appeals/{taskId}/punish")
    public ApiResponse<Void> punishAppeal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId) {

        Long adminUserId = principal.getUserId();
        taskModerationService.punishAppeal(adminUserId, taskId);
        return ApiResponse.success();
    }
}
