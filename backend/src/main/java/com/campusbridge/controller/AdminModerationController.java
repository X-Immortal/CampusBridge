package com.campusbridge.controller;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.ReportService;
import com.campusbridge.service.VerificationService;
import com.campusbridge.service.impl.TaskModerationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminModerationController {
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String STATUS_DISPUTED = "DISPUTED";


    private final CurrentUserResolver currentUserResolver;
    private final ReportService reportService;
    private final VerificationService verificationService;
    private final TaskMapper taskMapper;
    private final TaskModerationService taskModerationService;

    public AdminModerationController(
            CurrentUserResolver currentUserResolver,
            ReportService reportService,
            VerificationService verificationService,
            TaskMapper taskMapper,
            TaskModerationService taskModerationService) {
        this.currentUserResolver = currentUserResolver;
        this.reportService = reportService;
        this.verificationService = verificationService;
        this.taskMapper = taskMapper;
        this.taskModerationService = taskModerationService;
    }

    @GetMapping("/moderation/tasks/pending")
    public Result listPendingTasks(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        int offset = (safePage - 1) * safeSize;

        Map<String, Object> params = new HashMap<>();
        params.put("status", STATUS_PENDING_REVIEW);
        params.put("includePrivate", true);
        params.put("offset", offset);
        params.put("limit", safeSize);

        List<Task> tasks = taskMapper.selectWithFilters(params);
        return Result.success(tasks);
    }

    @PostMapping("/moderation/tasks/{taskId}/approve")
    public Result approveTask(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId) {

        Long adminUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(taskModerationService.approveTask(adminUserId, taskId));
    }

    @PostMapping("/moderation/tasks/{taskId}/reject")
    public Result rejectTask(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody(required = false) Map<String, String> request) {

        Long adminUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        String reason = request == null ? null : request.get("reason");
        return Result.success(taskModerationService.rejectTask(adminUserId, taskId, reason));
    }

    @GetMapping("/reports")
    public Result listReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(reportService.listReportsForAdmin(status, page, size));
    }

    @PostMapping("/reports/{reportId}/resolve")
    public Result resolveReport(
            HttpServletRequest servletRequest,
            @PathVariable Long reportId,
            @RequestBody RequestDtos.ResolveReportRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return Result.success(reportService.resolveReport(currentUserId, reportId, request));
    }

    // === 认证审核 ===

    @GetMapping("/verifications/pending")
    public Result listPendingVerifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(verificationService.listPending(page, size));
    }

    @PostMapping("/verifications/{id}/approve")
    public Result approveVerification(
            HttpServletRequest servletRequest,
            @PathVariable Long id) {
        Long adminUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        Object result = verificationService.review(adminUserId, id, true, null);
        assertVerificationReviewResult(result);
        return Result.success(result);
    }

    @PostMapping("/verifications/{id}/reject")
    public Result rejectVerification(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Long adminUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        Object result = verificationService.review(adminUserId, id, false, reason);
        assertVerificationReviewResult(result);
        return Result.success(result);
    }

    // === 申诉处理 ===

    @GetMapping("/appeals")
    public Result listAppeals(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        int offset = (safePage - 1) * safeSize;

        Map<String, Object> params = new HashMap<>();
        params.put("status", STATUS_DISPUTED);
        params.put("offset", offset);
        params.put("limit", safeSize);

        List<Task> tasks = taskMapper.selectWithFilters(params);
        long total = taskMapper.countWithFilters(params);

        Map<String, Object> result = new HashMap<>();
        result.put("items", tasks);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("total", total);
        return Result.success(result);
    }

    @PostMapping("/appeals/{taskId}/dismiss")
    public Result dismissAppeal(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId) {

        Long adminUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        taskModerationService.dismissAppeal(adminUserId, taskId);
        return Result.success();
    }

    @PostMapping("/appeals/{taskId}/punish")
    public Result punishAppeal(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId) {

        Long adminUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        taskModerationService.punishAppeal(adminUserId, taskId);
        return Result.success();
    }

    private void assertVerificationReviewResult(Object result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "审核操作失败");
        }
        if (result instanceof Long code) {
            if (code == -1L) {
                throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "认证记录不存在");
            }
            if (code == -2L) {
                throw new BusinessException(ErrorCode.COMMON_003_CONFLICT, "该认证记录已审核");
            }
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "审核操作失败");
        }
    }
}
