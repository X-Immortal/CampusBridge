package com.campusbridge.controller;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.pojo.CampusVerification;
import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.ReportService;
import com.campusbridge.service.TaskHistoryService;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminModerationController {
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_REJECTED = "REJECTED";

    private final CurrentUserResolver currentUserResolver;
    private final ReportService reportService;
    private final VerificationService verificationService;
    private final AuthContextUtil authContextUtil;
    private final TaskMapper taskMapper;
    private final TaskHistoryService taskHistoryService;

    public AdminModerationController(
            CurrentUserResolver currentUserResolver,
            ReportService reportService,
            VerificationService verificationService,
            AuthContextUtil authContextUtil,
            TaskMapper taskMapper,
            TaskHistoryService taskHistoryService) {
        this.currentUserResolver = currentUserResolver;
        this.reportService = reportService;
        this.verificationService = verificationService;
        this.authContextUtil = authContextUtil;
        this.taskMapper = taskMapper;
        this.taskHistoryService = taskHistoryService;
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

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }
        if (!STATUS_PENDING_REVIEW.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION,
                    "Only PENDING_REVIEW tasks can be approved");
        }

        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, STATUS_PENDING_REVIEW, STATUS_OPEN, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        taskHistoryService.recordHistory(
                taskId, STATUS_PENDING_REVIEW, STATUS_OPEN, adminUserId,
                "ADMIN_APPROVED");

        task.setStatus(STATUS_OPEN);
        task.setUpdatedAt(now);
        return Result.success(task);
    }

    @PostMapping("/moderation/tasks/{taskId}/reject")
    public Result rejectTask(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody(required = false) Map<String, String> request) {
        Long adminUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        String reason = request != null ? request.getOrDefault("reason", "") : "";

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }
        if (!STATUS_PENDING_REVIEW.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION,
                    "Only PENDING_REVIEW tasks can be rejected");
        }

        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, STATUS_PENDING_REVIEW, STATUS_REJECTED, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        String historyNote = "ADMIN_REJECTED";
        if (reason != null && !reason.isBlank()) {
            historyNote += "; REASON=" + reason;
        }
        taskHistoryService.recordHistory(
                taskId, STATUS_PENDING_REVIEW, STATUS_REJECTED, adminUserId,
                historyNote);

        task.setStatus(STATUS_REJECTED);
        task.setUpdatedAt(now);
        return Result.success(task);
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
