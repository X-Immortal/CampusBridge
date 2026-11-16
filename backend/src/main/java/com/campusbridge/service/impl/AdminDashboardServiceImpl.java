package com.campusbridge.service.impl;

import com.campusbridge.enums.DashboardTodoType;
import com.campusbridge.dto.view.CategoryCount;
import com.campusbridge.dto.view.DailyCount;
import com.campusbridge.dto.view.DashboardDistributionItem;
import com.campusbridge.dto.view.DashboardSummary;
import com.campusbridge.dto.view.DashboardTodo;
import com.campusbridge.dto.view.DashboardTrend;
import com.campusbridge.mapper.AdminDashboardMapper;
import com.campusbridge.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int DEFAULT_DAYS = 7;
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 30;
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    private final AdminDashboardMapper adminDashboardMapper;

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
        return toDistribution(adminDashboardMapper.countTasksByStatus());
    }

    @Override
    public List<DashboardDistributionItem> getReportStatusStats() {
        return toDistribution(adminDashboardMapper.countReportsByStatus());
    }

    @Override
    public List<DashboardDistributionItem> getReportTargetTypeStats() {
        return toDistribution(adminDashboardMapper.countReportsByTargetType());
    }

    @Override
    public List<DashboardTodo> getTodos() {
        long pendingVerifications = safeCount(adminDashboardMapper.countPendingVerifications());
        long pendingReports = safeCount(adminDashboardMapper.countPendingReports());
        long disputedTasks = safeCount(adminDashboardMapper.countDisputedTasks());

        List<DashboardTodo> todos = new ArrayList<>();
        todos.add(new DashboardTodo(
                DashboardTodoType.VERIFICATION,
                "待认证申请",
                pendingVerifications,
                "/admin?section=verifications"));
        todos.add(new DashboardTodo(
                DashboardTodoType.REPORT,
                "待处理举报",
                pendingReports,
                "/admin?section=reports"));
        todos.add(new DashboardTodo(
                DashboardTodoType.DISPUTED_TASK,
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

    private List<DashboardDistributionItem> toDistribution(List<CategoryCount> rows) {
        return rows.stream()
                .map(row -> new DashboardDistributionItem(row.getCategory(), row.getCount()))
                .toList();
    }
}
