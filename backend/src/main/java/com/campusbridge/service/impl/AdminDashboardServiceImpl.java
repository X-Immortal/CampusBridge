package com.campusbridge.service.impl;

import com.campusbridge.dto.view.CategoryCount;
import com.campusbridge.dto.view.DailyCount;
import com.campusbridge.dto.view.DashboardDistributionItem;
import com.campusbridge.dto.view.DashboardSummary;
import com.campusbridge.dto.view.DashboardTodo;
import com.campusbridge.dto.view.DashboardTrend;
import com.campusbridge.mapper.AdminDashboardMapper;
import com.campusbridge.service.AdminDashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Transactional(readOnly = true)
@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int DEFAULT_DAYS = 7;
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 30;
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    private static final Map<String, String> TASK_STATUS_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> REPORT_STATUS_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> REPORT_TARGET_TYPE_LABELS = new LinkedHashMap<>();

    static {
        TASK_STATUS_LABELS.put("OPEN", "待接单");
        TASK_STATUS_LABELS.put("ACCEPTED", "已选人");
        TASK_STATUS_LABELS.put("IN_PROGRESS", "进行中");
        TASK_STATUS_LABELS.put("PENDING_CONFIRMATION", "待确认");
        TASK_STATUS_LABELS.put("COMPLETED", "已完成");
        TASK_STATUS_LABELS.put("REVIEWED", "已评价");
        TASK_STATUS_LABELS.put("PENDING_REVIEW", "待审核");
        TASK_STATUS_LABELS.put("CANCELLED", "已取消");
        TASK_STATUS_LABELS.put("DISPUTED", "争议中");
        TASK_STATUS_LABELS.put("REJECTED", "未通过");
        TASK_STATUS_LABELS.put("DRAFT", "草稿");

        REPORT_STATUS_LABELS.put("PENDING", "待处理");
        REPORT_STATUS_LABELS.put("PROCESSING", "处理中");
        REPORT_STATUS_LABELS.put("APPROVED", "已通过");
        REPORT_STATUS_LABELS.put("REJECTED", "已驳回");
        REPORT_STATUS_LABELS.put("RESOLVED", "已处理");
        REPORT_STATUS_LABELS.put("PUNISHED", "已处罚");

        REPORT_TARGET_TYPE_LABELS.put("POST", "帖子");
        REPORT_TARGET_TYPE_LABELS.put("REPLY", "回复");
        REPORT_TARGET_TYPE_LABELS.put("TASK", "任务");
        REPORT_TARGET_TYPE_LABELS.put("USER", "用户");
        REPORT_TARGET_TYPE_LABELS.put("MESSAGE", "消息");
    }

    private final AdminDashboardMapper adminDashboardMapper;

    public AdminDashboardServiceImpl(AdminDashboardMapper adminDashboardMapper) {
        this.adminDashboardMapper = adminDashboardMapper;
    }

    @Override
    public DashboardSummary getSummary() {
        DashboardSummary summary = new DashboardSummary();
        summary.setUserTotal(safeCount(adminDashboardMapper.countAllUsers()));
        summary.setVerifiedUserTotal(safeCount(adminDashboardMapper.countVerifiedUsers()));
        summary.setTaskTotal(safeCount(adminDashboardMapper.countAllTasks()));
        summary.setActiveTaskTotal(safeCount(adminDashboardMapper.countActiveTasks()));
        summary.setCompletedTaskTotal(safeCount(adminDashboardMapper.countCompletedTasks()));
        summary.setPostTotal(safeCount(adminDashboardMapper.countAllPosts()));
        summary.setPendingVerificationTotal(safeCount(adminDashboardMapper.countPendingVerifications()));
        summary.setPendingReportTotal(safeCount(adminDashboardMapper.countPendingReports()));
        summary.setDisputedTaskTotal(safeCount(adminDashboardMapper.countDisputedTasks()));
        summary.setUpdatedAt(LocalDateTime.now());
        return summary;
    }

    @Override
    public DashboardTrend getTrends(int days) {
        int safeDays = normalizeDays(days, DEFAULT_DAYS);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(safeDays - 1L);
        LocalDateTime from = startDate.atStartOfDay();

        List<String> daysList = new ArrayList<>();
        Map<LocalDate, Long> userMap = aggregateByDay(adminDashboardMapper.countNewUsersByDay(from));
        Map<LocalDate, Long> taskMap = aggregateByDay(adminDashboardMapper.countNewTasksByDay(from));
        Map<LocalDate, Long> postMap = aggregateByDay(adminDashboardMapper.countNewPostsByDay(from));

        List<Long> newUsers = new ArrayList<>();
        List<Long> newTasks = new ArrayList<>();
        List<Long> newPosts = new ArrayList<>();

        for (int i = 0; i < safeDays; i++) {
            LocalDate day = startDate.plusDays(i);
            daysList.add(day.format(DAY_FORMATTER));
            newUsers.add(userMap.getOrDefault(day, 0L));
            newTasks.add(taskMap.getOrDefault(day, 0L));
            newPosts.add(postMap.getOrDefault(day, 0L));
        }

        return new DashboardTrend(daysList, newUsers, newTasks, newPosts);
    }

    @Override
    public List<DashboardDistributionItem> getTaskStatusStats() {
        return buildDistribution(adminDashboardMapper.countTasksByStatus(), TASK_STATUS_LABELS.keySet());
    }

    @Override
    public List<DashboardDistributionItem> getReportStatusStats() {
        return buildDistribution(adminDashboardMapper.countReportsByStatus(), REPORT_STATUS_LABELS.keySet());
    }

    @Override
    public List<DashboardDistributionItem> getReportTargetTypeStats() {
        return buildDistribution(adminDashboardMapper.countReportsByTargetType(), REPORT_TARGET_TYPE_LABELS.keySet());
    }

    @Override
    public List<DashboardTodo> getTodos() {
        long pendingVerifications = safeCount(adminDashboardMapper.countPendingVerifications());
        long pendingReports = safeCount(adminDashboardMapper.countPendingReports());
        long disputedTasks = safeCount(adminDashboardMapper.countDisputedTasks());

        List<DashboardTodo> todos = new ArrayList<>();
        todos.add(new DashboardTodo(
                "VERIFICATION",
                "待认证申请",
                pendingVerifications,
                "/admin?section=verifications"));
        todos.add(new DashboardTodo(
                "REPORT",
                "待处理举报",
                pendingReports,
                "/admin?section=reports"));
        todos.add(new DashboardTodo(
                "DISPUTED_TASK",
                "争议任务",
                disputedTasks,
                "/admin?section=dashboard"));
        return todos;
    }

    private long safeCount(Long value) {
        return value == null ? 0L : value;
    }

    private int normalizeDays(Integer days, int defaultValue) {
        if (days == null) {
            return defaultValue;
        }
        if (days < MIN_DAYS) {
            return MIN_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }

    private Map<LocalDate, Long> aggregateByDay(List<DailyCount> rows) {
        Map<LocalDate, Long> result = new HashMap<>();
        for (DailyCount row : rows) {
            result.put(row.getDay(), row.getCount());
        }
        return result;
    }

    private List<DashboardDistributionItem> buildDistribution(
            List<CategoryCount> rows,
            Set<String> knownKeys) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String key : knownKeys) {
            counts.put(key, 0L);
        }
        if (rows != null) {
            for (CategoryCount row : rows) {
                counts.put(row.getCategory(), row.getCount());
            }
        }
        List<DashboardDistributionItem> items = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            String status = entry.getKey();
            String label = resolveLabel(status);
            items.add(new DashboardDistributionItem(status, label, entry.getValue()));
        }
        return items;
    }

    private String resolveLabel(String key) {
        if (TASK_STATUS_LABELS.containsKey(key)) {
            return TASK_STATUS_LABELS.get(key);
        }
        if (REPORT_STATUS_LABELS.containsKey(key)) {
            return REPORT_STATUS_LABELS.get(key);
        }
        if (REPORT_TARGET_TYPE_LABELS.containsKey(key)) {
            return REPORT_TARGET_TYPE_LABELS.get(key);
        }
        return key;
    }
}
