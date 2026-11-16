package com.campusbridge.service.impl;

import com.campusbridge.enums.CreditSourceType;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.request.CreateReportRequest;
import com.campusbridge.dto.request.ResolveReportRequest;
import com.campusbridge.entity.Message;
import com.campusbridge.entity.ModerationRecord;
import com.campusbridge.entity.Post;
import com.campusbridge.entity.Reply;
import com.campusbridge.entity.Report;
import com.campusbridge.entity.Task;
import com.campusbridge.entity.User;
import com.campusbridge.enums.ReportStatus;
import com.campusbridge.enums.ReportTargetType;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.enums.TaskHistoryEventType;
import com.campusbridge.enums.TaskStatusTransitionAction;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.mapper.ModerationRecordMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.mapper.ReportMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.TaskNotificationService;
import com.campusbridge.service.ReportService;
import com.campusbridge.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int DEFAULT_PUNISHMENT_CREDIT_CHANGE = -10;


    private final ReportMapper reportMapper;
    private final ModerationRecordMapper moderationRecordMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final ReplyMapper replyMapper;
    private final MessageMapper messageMapper;
    private final TaskHistoryService taskHistoryService;
    private final TaskNotificationService notificationService;
    private final CreditService creditService;

    @Override
    public Report createReport(Long reporterId, CreateReportRequest request) {
        validateReporterId(reporterId);

        Object target = requireTargetExists(reporterId, request.getTargetType(), request.getTargetId());
        LocalDateTime now = LocalDateTime.now();

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(request.getTargetType());
        report.setTargetId(request.getTargetId());
        report.setReason(request.getReason().trim());
        report.setDescription(trimToNull(request.getDescription()));
        report.setStatus(ReportStatus.PENDING);
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
    public PagedResult<Report> listReports(
            Long reporterId,
            ReportStatus status,
            Integer page,
            Integer size) {
        validateReporterId(reporterId);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        Page<Report> result = reportMapper.selectPage(new Page<>(safePage, safeSize),
                Wrappers.<Report>lambdaQuery()
                        .eq(Report::getReporterId, reporterId)
                        .eq(status != null, Report::getStatus, status)
                        .orderByDesc(Report::getCreatedAt, Report::getId));
        return new PagedResult<>(result.getRecords(), safePage, safeSize, result.getTotal());
    }

    @Override
    public PagedResult<Report> listReportsForAdmin(
            ReportStatus status,
            Integer page,
            Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        Page<Report> result = reportMapper.selectPage(new Page<>(safePage, safeSize),
                Wrappers.<Report>lambdaQuery()
                        .eq(status != null, Report::getStatus, status)
                        .orderByDesc(Report::getCreatedAt, Report::getId));
        return new PagedResult<>(result.getRecords(), safePage, safeSize, result.getTotal());
    }

    @Override
    public ModerationRecord resolveReport(
            Long handlerId,
            Long reportId,
            ResolveReportRequest request) {
        validateHandlerId(handlerId);
        validateReportId(reportId);
        validateResolveReportRequest(request);

        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.REPORT_001_NOT_FOUND);
        }
        if (report.getStatus() == null || report.getStatus().isTerminal()) {
            throw new BusinessException(
                    ErrorCode.COMMON_003_CONFLICT,
                    "Report has already been resolved");
        }

        ReportStatus status = request.getStatus();
        if (reportMapper.updateStatusById(reportId, status) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_003_CONFLICT,
                    "Report has already been resolved");
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

        if (Boolean.TRUE.equals(request.getMarkTaskDisputed()) && report.getTargetType() == ReportTargetType.TASK) {
            Task task = taskMapper.selectById(report.getTargetId());
            if (task == null) {
                throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
            }
            markTaskDisputedByAdmin(handlerId, task, report);
        }

        applyCreditChangeIfNeeded(report, request);
        return record;
    }

    private void validateResolveReportRequest(ResolveReportRequest request) {
        if (!request.getStatus().isTerminal()) {
            throwValidation("status must be a final moderation result");
        }
        if (request.getCreditChange() != null && request.getCreditChange() == 0) {
            throwValidation("creditChange must not be zero");
        }
    }

    private Object requireTargetExists(Long reporterId, ReportTargetType targetType, Long targetId) {
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
                Message message = messageMapper.selectOwnedById(targetId, reporterId);
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
        if (!isTaskParticipant(task, operatorId) || !TaskStatusTransitionAction.DISPUTE.canTransitionFrom(task.getStatus())) {
            return;
        }

        TaskStatus fromStatus = task.getStatus();
        LocalDateTime now = LocalDateTime.now();
        if (taskMapper.updateStatusById(task.getId(), fromStatus, TaskStatus.DISPUTED, now) != 1) {
            return;
        }

        taskHistoryService.recordHistory(
                task.getId(),
                fromStatus,
                TaskStatus.DISPUTED,
                operatorId,
                TaskHistoryEventType.REPORT_CREATED, buildReportHistoryNote(report));
        notificationService.notifyTaskStatusChanged(
                task,
                TaskStatus.DISPUTED,
                operatorId,
                report.getReason());
        task.setStatus(TaskStatus.DISPUTED);
        task.setUpdatedAt(now);
    }

    private void markTaskDisputedByAdmin(Long adminUserId, Task task, Report report) {
        if (task == null || report == null || task.getId() == null) {
            return;
        }
        if (!TaskStatusTransitionAction.DISPUTE.canTransitionFrom(task.getStatus())) {
            return;
        }

        TaskStatus fromStatus = task.getStatus();
        LocalDateTime now = LocalDateTime.now();
        if (taskMapper.updateStatusById(task.getId(), fromStatus, TaskStatus.DISPUTED, now) != 1) {
            return;
        }

        taskHistoryService.recordHistory(
                task.getId(),
                fromStatus,
                TaskStatus.DISPUTED,
                adminUserId,
                TaskHistoryEventType.REPORT_RESOLVED, buildReportHistoryNote(report));
        notificationService.notifyTaskStatusChanged(
                task,
                TaskStatus.DISPUTED,
                adminUserId,
                report.getReason());
        task.setStatus(TaskStatus.DISPUTED);
        task.setUpdatedAt(now);
    }

    private void applyCreditChangeIfNeeded(
            Report report,
            ResolveReportRequest request) {
        Integer creditChange = request.getCreditChange();
        if (creditChange == null && request.getStatus() == ReportStatus.PUNISHED) {
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
                CreditSourceType.REPORT,
                report.getId());
    }

    private Long resolveDefaultCreditTargetUserId(Report report) {
        ReportTargetType targetType = report.getTargetType();
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

    private String buildCreditReason(ResolveReportRequest request) {
        return "REPORT_" + request.getStatus().name() + ": " + request.getDecision().trim();
    }

    private String buildReportHistoryNote(Report report) {
        String note = "举报 #" + report.getId();
        if (report.getReason() != null && !report.getReason().isBlank()) {
            note += "；原因：" + report.getReason().trim();
        }
        return note.length() <= 300 ? note : note.substring(0, 300);
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

    private void throwValidation(String message) {
        throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, message);
    }
}
