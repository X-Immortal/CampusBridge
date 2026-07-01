package com.campusbridge.controller;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.ReportService;
import com.campusbridge.service.TaskHistoryService;
import com.campusbridge.service.VerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final String STATUS_DISPUTED = "DISPUTED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final int APPEAL_PUNISH_CREDIT = -20;

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminModerationController.class);

    private final CurrentUserResolver currentUserResolver;
    private final ReportService reportService;
    private final VerificationService verificationService;
    private final TaskMapper taskMapper;
    private final TaskHistoryService taskHistoryService;
    private final CreditService creditService;
    private final NotificationService notificationService;

    public AdminModerationController(
            CurrentUserResolver currentUserResolver,
            ReportService reportService,
            VerificationService verificationService,
            TaskMapper taskMapper,
            TaskHistoryService taskHistoryService,
            CreditService creditService,
            NotificationService notificationService) {
        this.currentUserResolver = currentUserResolver;
        this.reportService = reportService;
        this.verificationService = verificationService;
        this.taskMapper = taskMapper;
        this.taskHistoryService = taskHistoryService;
        this.creditService = creditService;
        this.notificationService = notificationService;
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

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }
        if (!STATUS_DISPUTED.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION,
                    "只有 DISPUTED 状态的任务才能驳回申诉");
        }

        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, STATUS_DISPUTED, STATUS_COMPLETED, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        settlePointsReward(task);

        taskHistoryService.recordHistory(
                taskId, STATUS_DISPUTED, STATUS_COMPLETED, adminUserId,
                "ADMIN_DISMISSED_APPEAL");
        notificationService.notifyTaskStatusChanged(
                task, STATUS_DISPUTED, STATUS_COMPLETED, adminUserId,
                "管理员已驳回申诉，任务正常完成。");

        LOGGER.info("Admin #{} dismissed appeal for task #{}", adminUserId, taskId);
        return Result.success();
    }

    @PostMapping("/appeals/{taskId}/punish")
    public Result punishAppeal(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId) {
        Long adminUserId = currentUserResolver.requireCurrentUserId(servletRequest);

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }
        if (!STATUS_DISPUTED.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION,
                    "只有 DISPUTED 状态的任务才能处罚");
        }

        LocalDateTime now = LocalDateTime.now();
        int affectedRows = taskMapper.updateStatusById(taskId, STATUS_DISPUTED, STATUS_COMPLETED, now);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }

        if (task.getProviderId() != null) {
            creditService.adjustCredit(
                    task.getProviderId(),
                    APPEAL_PUNISH_CREDIT,
                    "任务 #" + taskId + " 申诉处罚扣分",
                    "TASK",
                    taskId);
        }

        taskHistoryService.recordHistory(
                taskId, STATUS_DISPUTED, STATUS_COMPLETED, adminUserId,
                "ADMIN_PUNISHED_APPEAL; PROVIDER_DEDUCTED_20");
        notificationService.notifyTaskStatusChanged(
                task, STATUS_DISPUTED, STATUS_COMPLETED, adminUserId,
                "管理员已支持申诉并对执行者扣除20积分。");

        LOGGER.info("Admin #{} punished appeal for task #{}, provider #{} deducted {} credits",
                adminUserId, taskId, task.getProviderId(), APPEAL_PUNISH_CREDIT);
        return Result.success();
    }

    private void settlePointsReward(Task task) {
        if (!"POINTS".equals(task.getRewardType())) {
            return;
        }
        Integer amount = task.getRewardAmount();
        if (amount == null || amount <= 0) {
            return;
        }
        Long providerId = task.getProviderId();
        if (providerId == null) {
            return;
        }
        creditService.adjustCredit(providerId, +amount,
                "任务 #" + task.getId() + " 积分报酬收入", "TASK", task.getId());
        LOGGER.info("Settled {} points reward to provider #{} for task #{}", amount, providerId, task.getId());
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
