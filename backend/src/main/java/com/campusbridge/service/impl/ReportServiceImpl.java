package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.mapper.ModerationRecordMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.mapper.ReportMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.pojo.Message;
import com.campusbridge.pojo.ModerationRecord;
import com.campusbridge.pojo.Post;
import com.campusbridge.pojo.Reply;
import com.campusbridge.pojo.Report;
import com.campusbridge.pojo.Task;
import com.campusbridge.pojo.User;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.ReportService;
import com.campusbridge.service.TaskHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Transactional
@Service
public class ReportServiceImpl implements ReportService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_REASON_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_DECISION_LENGTH = 500;
    private static final int DEFAULT_PUNISHMENT_CREDIT_CHANGE = -10;

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_PUNISHED = "PUNISHED";
    private static final String STATUS_DISPUTED = "DISPUTED";
    private static final String TARGET_TASK = "TASK";
    private static final String SOURCE_TYPE_REPORT = "REPORT";

    private static final Set<String> DISPUTABLE_TASK_STATUSES =
            Set.of("ACCEPTED", "IN_PROGRESS", "PENDING_CONFIRMATION");

    private final ReportMapper reportMapper;
    private final ModerationRecordMapper moderationRecordMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final ReplyMapper replyMapper;
    private final MessageMapper messageMapper;
    private final TaskHistoryService taskHistoryService;
    private final NotificationService notificationService;
    private final CreditService creditService;

    public ReportServiceImpl(
            ReportMapper reportMapper,
            ModerationRecordMapper moderationRecordMapper,
            TaskMapper taskMapper,
            UserMapper userMapper,
            PostMapper postMapper,
            ReplyMapper replyMapper,
            MessageMapper messageMapper,
            TaskHistoryService taskHistoryService,
            NotificationService notificationService,
            CreditService creditService) {
        this.reportMapper = reportMapper;
        this.moderationRecordMapper = moderationRecordMapper;
        this.taskMapper = taskMapper;
        this.userMapper = userMapper;
        this.postMapper = postMapper;
        this.replyMapper = replyMapper;
        this.messageMapper = messageMapper;
        this.taskHistoryService = taskHistoryService;
        this.notificationService = notificationService;
        this.creditService = creditService;
    }

    @Override
    public Report createReport(Long reporterId, RequestDtos.CreateReportRequest request) {
        validateReporterId(reporterId);
        validateCreateReportRequest(request);

        Object target = requireTargetExists(request.getTargetType(), request.getTargetId());
        LocalDateTime now = LocalDateTime.now();

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(request.getTargetType().name());
        report.setTargetId(request.getTargetId());
        report.setReason(request.getReason().trim());
        report.setDescription(trimToNull(request.getDescription()));
        report.setStatus(STATUS_PENDING);
        report.setCreatedAt(now);

        if (reportMapper.insert(report) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create report");
        }

        if (target instanceof Task task) {
            markTaskDisputedIfAllowed(reporterId, task, report);
        }
        return report;
    }

    @Override
    public ResponseDtos.PagedResult<Report> listReports(
            Long reporterId,
            String status,
            Integer page,
            Integer size) {
        validateReporterId(reporterId);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        String normalizedStatus = normalizeStatusFilter(status);
        int offset = (safePage - 1) * safeSize;

        List<Report> reports = reportMapper.selectByReporterId(
                reporterId,
                normalizedStatus,
                offset,
                safeSize);
        long total = reportMapper.countByReporterId(reporterId, normalizedStatus);
        return new ResponseDtos.PagedResult<>(reports, safePage, safeSize, total);
    }

    @Override
    public ResponseDtos.PagedResult<Report> listReportsForAdmin(
            String status,
            Integer page,
            Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        String normalizedStatus = normalizeStatusFilter(status);
        int offset = (safePage - 1) * safeSize;

        List<Report> reports = reportMapper.selectForAdmin(
                normalizedStatus,
                offset,
                safeSize);
        long total = reportMapper.countForAdmin(normalizedStatus);
        return new ResponseDtos.PagedResult<>(reports, safePage, safeSize, total);
    }

    @Override
    public ModerationRecord resolveReport(
            Long handlerId,
            Long reportId,
            RequestDtos.ResolveReportRequest request) {
        validateHandlerId(handlerId);
        validateReportId(reportId);
        validateResolveReportRequest(request);

        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.REPORT_001_NOT_FOUND);
        }
        if (!STATUS_PENDING.equals(report.getStatus()) && !STATUS_PROCESSING.equals(report.getStatus())) {
            throw new BusinessException(
                    ErrorCode.COMMON_003_CONFLICT,
                    "Report has already been resolved");
        }

        String status = request.getStatus().name();
        if (reportMapper.updateStatusById(reportId, status) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to update report status");
        }
        report.setStatus(status);

        ModerationRecord record = new ModerationRecord();
        record.setReportId(reportId);
        record.setTargetType(report.getTargetType());
        record.setTargetId(report.getTargetId());
        record.setHandlerId(handlerId);
        record.setStatus(status);
        record.setDecision(request.getDecision().trim());
        record.setHandledAt(LocalDateTime.now());

        if (moderationRecordMapper.insert(record) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create moderation record");
        }

        if (Boolean.TRUE.equals(request.getMarkTaskDisputed()) && TARGET_TASK.equals(report.getTargetType())) {
            Task task = taskMapper.selectById(report.getTargetId());
            if (task == null) {
                throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
            }
            markTaskDisputedByAdmin(handlerId, task, report);
        }

        applyCreditChangeIfNeeded(report, request);
        return record;
    }

    private void validateCreateReportRequest(RequestDtos.CreateReportRequest request) {
        if (request == null) {
            throwValidation("Request body must not be null");
        }
        if (request.getTargetType() == null) {
            throwValidation("targetType is required");
        }
        if (request.getTargetId() == null) {
            throwValidation("targetId is required");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throwValidation("reason is required");
        }
        validateLength(request.getReason(), MAX_REASON_LENGTH, "reason is too long");
        validateLength(request.getDescription(), MAX_DESCRIPTION_LENGTH, "description is too long");
    }

    private void validateResolveReportRequest(RequestDtos.ResolveReportRequest request) {
        if (request == null) {
            throwValidation("Request body must not be null");
        }
        if (request.getStatus() == null) {
            throwValidation("status is required");
        }
        if (request.getDecision() == null || request.getDecision().isBlank()) {
            throwValidation("decision is required");
        }
        validateLength(request.getDecision(), MAX_DECISION_LENGTH, "decision is too long");
        if (STATUS_PENDING.equals(request.getStatus().name())
                || STATUS_PROCESSING.equals(request.getStatus().name())) {
            throwValidation("status must be a final moderation result");
        }
        if (request.getCreditChange() != null && request.getCreditChange() == 0) {
            throwValidation("creditChange must not be zero");
        }
    }

    private Object requireTargetExists(ApiEnums.ReportTargetType targetType, Long targetId) {
        switch (targetType) {
            case POST:
                Post post = postMapper.selectById(targetId);
                if (post == null) {
                    throw new BusinessException(ErrorCode.POST_001_NOT_FOUND);
                }
                return post;
            case REPLY:
                Reply reply = replyMapper.selectById(targetId);
                if (reply == null) {
                    throw new BusinessException(ErrorCode.POST_003_REPLY_NOT_FOUND);
                }
                return reply;
            case TASK:
                Task task = taskMapper.selectById(targetId);
                if (task == null) {
                    throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
                }
                return task;
            case USER:
                User user = userMapper.selectById(targetId);
                if (user == null) {
                    throw new BusinessException(ErrorCode.USER_001_NOT_FOUND);
                }
                return user;
            case MESSAGE:
                Message message = messageMapper.selectById(targetId);
                if (message == null) {
                    throwValidation("message target not found");
                }
                return message;
            default:
                throwValidation("Unsupported report target type");
                return null;
        }
    }

    private void markTaskDisputedIfAllowed(Long operatorId, Task task, Report report) {
        if (task == null || report == null || task.getId() == null) {
            return;
        }
        if (!isTaskParticipant(task, operatorId) || !DISPUTABLE_TASK_STATUSES.contains(task.getStatus())) {
            return;
        }

        String fromStatus = task.getStatus();
        LocalDateTime now = LocalDateTime.now();
        if (taskMapper.updateStatusById(task.getId(), fromStatus, STATUS_DISPUTED, now) != 1) {
            return;
        }

        taskHistoryService.recordHistory(
                task.getId(),
                fromStatus,
                STATUS_DISPUTED,
                operatorId,
                buildReportHistoryNote(report));
        notificationService.notifyTaskStatusChanged(
                task,
                fromStatus,
                STATUS_DISPUTED,
                operatorId,
                report.getReason());
        task.setStatus(STATUS_DISPUTED);
        task.setUpdatedAt(now);
    }

    private void markTaskDisputedByAdmin(Long adminUserId, Task task, Report report) {
        if (task == null || report == null || task.getId() == null) {
            return;
        }
        if (!DISPUTABLE_TASK_STATUSES.contains(task.getStatus())) {
            return;
        }

        String fromStatus = task.getStatus();
        LocalDateTime now = LocalDateTime.now();
        if (taskMapper.updateStatusById(task.getId(), fromStatus, STATUS_DISPUTED, now) != 1) {
            return;
        }

        taskHistoryService.recordHistory(
                task.getId(),
                fromStatus,
                STATUS_DISPUTED,
                adminUserId,
                buildAdminReportHistoryNote(report));
        notificationService.notifyTaskStatusChanged(
                task,
                fromStatus,
                STATUS_DISPUTED,
                adminUserId,
                report.getReason());
        task.setStatus(STATUS_DISPUTED);
        task.setUpdatedAt(now);
    }

    private void applyCreditChangeIfNeeded(
            Report report,
            RequestDtos.ResolveReportRequest request) {
        Integer creditChange = request.getCreditChange();
        if (creditChange == null && STATUS_PUNISHED.equals(request.getStatus().name())) {
            creditChange = DEFAULT_PUNISHMENT_CREDIT_CHANGE;
        }
        if (creditChange == null) {
            return;
        }

        Long userId = request.getPunishedUserId();
        if (userId == null) {
            userId = resolveDefaultCreditTargetUserId(report);
        }
        if (userId == null) {
            return;
        }
        requireUserExists(userId);

        creditService.adjustCredit(
                userId,
                creditChange,
                buildCreditReason(request),
                SOURCE_TYPE_REPORT,
                report.getId());
    }

    private Long resolveDefaultCreditTargetUserId(Report report) {
        ApiEnums.ReportTargetType targetType = ApiEnums.ReportTargetType.valueOf(report.getTargetType());
        switch (targetType) {
            case POST:
                Post post = postMapper.selectById(report.getTargetId());
                return post == null ? null : post.getAuthorId();
            case REPLY:
                Reply reply = replyMapper.selectById(report.getTargetId());
                return reply == null ? null : reply.getAuthorId();
            case TASK:
                Task task = taskMapper.selectById(report.getTargetId());
                return task == null ? null : resolveTaskCreditTargetUserId(task, report.getReporterId());
            case USER:
                return report.getTargetId();
            case MESSAGE:
                Message message = messageMapper.selectById(report.getTargetId());
                return message == null ? null : message.getSenderId();
            default:
                return null;
        }
    }

    private Long resolveTaskCreditTargetUserId(Task task, Long reporterId) {
        if (task == null) {
            return null;
        }
        if (reporterId != null
                && reporterId.equals(task.getRequesterId())
                && task.getProviderId() != null) {
            return task.getProviderId();
        }
        if (reporterId != null && reporterId.equals(task.getProviderId())) {
            return task.getRequesterId();
        }
        return task.getRequesterId();
    }

    private void requireUserExists(Long userId) {
        if (userId == null || userMapper.selectById(userId) == null) {
            throw new BusinessException(ErrorCode.USER_001_NOT_FOUND);
        }
    }

    private String buildCreditReason(RequestDtos.ResolveReportRequest request) {
        return "REPORT_" + request.getStatus().name() + ": " + request.getDecision().trim();
    }

    private String buildReportHistoryNote(Report report) {
        String note = "REPORT_CREATED; REPORT_ID=" + report.getId();
        if (report.getReason() == null || report.getReason().isBlank()) {
            return note;
        }
        String reason = report.getReason().trim();
        String combined = note + "; REASON=" + reason;
        if (combined.length() <= 300) {
            return combined;
        }
        return combined.substring(0, 300);
    }

    private String buildAdminReportHistoryNote(Report report) {
        String note = "ADMIN_REPORT_RESOLVED; REPORT_ID=" + report.getId();
        if (report.getReason() == null || report.getReason().isBlank()) {
            return note;
        }
        String reason = report.getReason().trim();
        String combined = note + "; REASON=" + reason;
        if (combined.length() <= 300) {
            return combined;
        }
        return combined.substring(0, 300);
    }

    private boolean isTaskParticipant(Task task, Long userId) {
        if (task == null || userId == null) {
            return false;
        }
        return userId.equals(task.getRequesterId()) || userId.equals(task.getProviderId());
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        try {
            ApiEnums.ModerationStatus.valueOf(normalized);
            return normalized;
        } catch (IllegalArgumentException ex) {
            throwValidation("status is invalid");
            return null;
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateReporterId(Long reporterId) {
        if (reporterId == null) {
            throw new BusinessException(ErrorCode.AUTH_001_UNAUTHORIZED);
        }
    }

    private void validateHandlerId(Long handlerId) {
        if (handlerId == null) {
            throw new BusinessException(ErrorCode.AUTH_001_UNAUTHORIZED);
        }
    }

    private void validateReportId(Long reportId) {
        if (reportId == null) {
            throwValidation("reportId must not be null");
        }
    }

    private void validateLength(String value, int maxLength, String message) {
        if (value != null && value.length() > maxLength) {
            throwValidation(message);
        }
    }

    private void throwValidation(String message) {
        throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, message);
    }
}
