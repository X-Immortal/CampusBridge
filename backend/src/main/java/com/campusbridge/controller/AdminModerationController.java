package com.campusbridge.controller;

import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.CampusVerification;
import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.ModerationRecord;
import com.campusbridge.service.ReportService;
import com.campusbridge.service.VerificationService;
import com.campusbridge.utils.AuthContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminModerationController {
    private final CurrentUserResolver currentUserResolver;
    private final ReportService reportService;
    private final VerificationService verificationService;
    private final AuthContextUtil authContextUtil;

    public AdminModerationController(
            CurrentUserResolver currentUserResolver,
            ReportService reportService,
            VerificationService verificationService,
            AuthContextUtil authContextUtil) {
        this.currentUserResolver = currentUserResolver;
        this.reportService = reportService;
        this.verificationService = verificationService;
        this.authContextUtil = authContextUtil;
    }

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
        List<CampusVerification> list = verificationService.listPending(page, size);
        return Result.success(list);
    }

    @PostMapping("/verifications/{id}/approve")
    public Result approveVerification(@PathVariable Long id) {
        Long adminUserId = authContextUtil.getCurrentUserId();
        Object result = verificationService.review(adminUserId, id, true, null);
        if (result == null) {
            return Result.error("VERIFY_003_SUBMIT_FAILED", "审核操作失败");
        }
        if (result instanceof Long code) {
            if (code == -1L) {
                return Result.error("VERIFY_004_NOT_FOUND", "认证记录不存在");
            }
            if (code == -2L) {
                return Result.error("VERIFY_005_ALREADY_REVIEWED", "该认证记录已审核");
            }
            return Result.error("VERIFY_003_SUBMIT_FAILED", "审核操作失败");
        }
        return Result.success(result);
    }

    @PostMapping("/verifications/{id}/reject")
    public Result rejectVerification(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Long adminUserId = authContextUtil.getCurrentUserId();
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        Object result = verificationService.review(adminUserId, id, false, reason);
        if (result == null) {
            return Result.error("VERIFY_003_SUBMIT_FAILED", "审核操作失败");
        }
        if (result instanceof Long code) {
            if (code == -1L) {
                return Result.error("VERIFY_004_NOT_FOUND", "认证记录不存在");
            }
            if (code == -2L) {
                return Result.error("VERIFY_005_ALREADY_REVIEWED", "该认证记录已审核");
            }
            return Result.error("VERIFY_003_SUBMIT_FAILED", "审核操作失败");
        }
        return Result.success(result);
    }
}
