package com.campusbridge.unit;

import com.campusbridge.dto.view.ViewDtos.DashboardDistributionItem;
import com.campusbridge.dto.view.ViewDtos.DashboardSummary;
import com.campusbridge.dto.view.ViewDtos.DashboardTodo;
import com.campusbridge.dto.view.ViewDtos.DashboardTrend;
import com.campusbridge.mapper.AdminDashboardMapper;
import com.campusbridge.service.impl.AdminDashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BE17AdminDashboardServiceImplTest {

    @Mock
    private AdminDashboardMapper adminDashboardMapper;

    private AdminDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardServiceImpl(adminDashboardMapper);
    }

    @Test
    void summaryAggregatesAllCounters() {
        when(adminDashboardMapper.countAllUsers()).thenReturn(120L);
        when(adminDashboardMapper.countVerifiedUsers()).thenReturn(95L);
        when(adminDashboardMapper.countAllTasks()).thenReturn(340L);
        when(adminDashboardMapper.countActiveTasks()).thenReturn(68L);
        when(adminDashboardMapper.countCompletedTasks()).thenReturn(210L);
        when(adminDashboardMapper.countAllPosts()).thenReturn(180L);
        when(adminDashboardMapper.countPendingVerifications()).thenReturn(6L);
        when(adminDashboardMapper.countPendingReports()).thenReturn(4L);
        when(adminDashboardMapper.countDisputedTasks()).thenReturn(2L);

        DashboardSummary summary = service.getSummary();

        assertThat(summary.getUserTotal()).isEqualTo(120L);
        assertThat(summary.getVerifiedUserTotal()).isEqualTo(95L);
        assertThat(summary.getTaskTotal()).isEqualTo(340L);
        assertThat(summary.getActiveTaskTotal()).isEqualTo(68L);
        assertThat(summary.getCompletedTaskTotal()).isEqualTo(210L);
        assertThat(summary.getPostTotal()).isEqualTo(180L);
        assertThat(summary.getPendingVerificationTotal()).isEqualTo(6L);
        assertThat(summary.getPendingReportTotal()).isEqualTo(4L);
        assertThat(summary.getDisputedTaskTotal()).isEqualTo(2L);
        assertThat(summary.getUpdatedAt()).isNotNull();
    }

    @Test
    void summaryFallsBackToZeroForZeroCounters() {
        when(adminDashboardMapper.countAllUsers()).thenReturn(0L);
        when(adminDashboardMapper.countVerifiedUsers()).thenReturn(0L);
        when(adminDashboardMapper.countAllTasks()).thenReturn(0L);
        when(adminDashboardMapper.countActiveTasks()).thenReturn(0L);
        when(adminDashboardMapper.countCompletedTasks()).thenReturn(0L);
        when(adminDashboardMapper.countAllPosts()).thenReturn(0L);
        when(adminDashboardMapper.countPendingVerifications()).thenReturn(0L);
        when(adminDashboardMapper.countPendingReports()).thenReturn(0L);
        when(adminDashboardMapper.countDisputedTasks()).thenReturn(0L);

        DashboardSummary summary = service.getSummary();

        assertThat(summary.getUserTotal()).isZero();
        assertThat(summary.getVerifiedUserTotal()).isZero();
        assertThat(summary.getTaskTotal()).isZero();
        assertThat(summary.getActiveTaskTotal()).isZero();
        assertThat(summary.getCompletedTaskTotal()).isZero();
        assertThat(summary.getPostTotal()).isZero();
        assertThat(summary.getPendingVerificationTotal()).isZero();
        assertThat(summary.getPendingReportTotal()).isZero();
        assertThat(summary.getDisputedTaskTotal()).isZero();
    }

    @Test
    void trendsFillMissingDaysWithZeroAndClampRange() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(2);

        List<Map<String, Object>> userRows = new ArrayList<>();
        userRows.add(row(start, 3L));
        userRows.add(row(today, 7L));

        List<Map<String, Object>> taskRows = new ArrayList<>();
        taskRows.add(row(start, 5L));
        taskRows.add(row(start.plusDays(1), 9L));

        List<Map<String, Object>> postRows = new ArrayList<>();
        postRows.add(row(today, 2L));

        when(adminDashboardMapper.countNewUsersByDay(any())).thenReturn(userRows);
        when(adminDashboardMapper.countNewTasksByDay(any())).thenReturn(taskRows);
        when(adminDashboardMapper.countNewPostsByDay(any())).thenReturn(postRows);

        DashboardTrend trend = service.getTrends(3);

        assertThat(trend.getDays()).hasSize(3);
        assertThat(trend.getDays().get(0)).isEqualTo(start.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd")));
        assertThat(trend.getDays().get(2)).isEqualTo(today.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd")));
        assertThat(trend.getNewUsers()).containsExactly(3L, 0L, 7L);
        assertThat(trend.getNewTasks()).containsExactly(5L, 9L, 0L);
        assertThat(trend.getNewPosts()).containsExactly(0L, 0L, 2L);
    }

    @Test
    void trendsClampToMaxRange() {
        when(adminDashboardMapper.countNewUsersByDay(any())).thenReturn(new ArrayList<>());
        when(adminDashboardMapper.countNewTasksByDay(any())).thenReturn(new ArrayList<>());
        when(adminDashboardMapper.countNewPostsByDay(any())).thenReturn(new ArrayList<>());

        DashboardTrend trend = service.getTrends(365);

        assertThat(trend.getDays()).hasSize(30);
    }

    @Test
    void taskStatusStatsFillsAllKnownStatuses() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("OPEN", 48L));
        rows.add(row("COMPLETED", 210L));

        when(adminDashboardMapper.countTasksByStatus()).thenReturn(rows);

        List<DashboardDistributionItem> stats = service.getTaskStatusStats();

        assertThat(stats).hasSize(11);
        DashboardDistributionItem open = stats.stream()
                .filter((item) -> "OPEN".equals(item.getStatus()))
                .findFirst()
                .orElseThrow();
        assertThat(open.getCount()).isEqualTo(48L);
        assertThat(open.getLabel()).isEqualTo("待接单");

        DashboardDistributionItem completed = stats.stream()
                .filter((item) -> "COMPLETED".equals(item.getStatus()))
                .findFirst()
                .orElseThrow();
        assertThat(completed.getCount()).isEqualTo(210L);

        DashboardDistributionItem cancelled = stats.stream()
                .filter((item) -> "CANCELLED".equals(item.getStatus()))
                .findFirst()
                .orElseThrow();
        assertThat(cancelled.getCount()).isZero();
    }

    @Test
    void taskStatusStatsKeepsUnknownStatusForCompatibility() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("FUTURE_STATUS", 9L));

        when(adminDashboardMapper.countTasksByStatus()).thenReturn(rows);

        List<DashboardDistributionItem> stats = service.getTaskStatusStats();

        assertThat(stats).hasSize(12);
        DashboardDistributionItem future = stats.stream()
                .filter((item) -> "FUTURE_STATUS".equals(item.getStatus()))
                .findFirst()
                .orElseThrow();
        assertThat(future.getCount()).isEqualTo(9L);
        assertThat(future.getLabel()).isEqualTo("FUTURE_STATUS");
    }

    @Test
    void reportStatusStatsFillsAllKnownStatuses() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("PENDING", 4L));
        rows.add(row("RESOLVED", 18L));

        when(adminDashboardMapper.countReportsByStatus()).thenReturn(rows);

        List<DashboardDistributionItem> stats = service.getReportStatusStats();

        assertThat(stats).hasSize(6);
        assertThat(stats.stream()
                .filter((item) -> "PENDING".equals(item.getStatus()))
                .findFirst()
                .orElseThrow()
                .getCount()).isEqualTo(4L);
        assertThat(stats.stream()
                .filter((item) -> "REJECTED".equals(item.getStatus()))
                .findFirst()
                .orElseThrow()
                .getCount()).isZero();
    }

    @Test
    void todosExposePendingCountsAsAdminSummary() {
        when(adminDashboardMapper.countPendingVerifications()).thenReturn(6L);
        when(adminDashboardMapper.countPendingReports()).thenReturn(4L);
        when(adminDashboardMapper.countDisputedTasks()).thenReturn(2L);

        List<DashboardTodo> todos = service.getTodos();

        assertThat(todos).hasSize(3);
        assertThat(todos.get(0).getType()).isEqualTo("VERIFICATION");
        assertThat(todos.get(0).getCount()).isEqualTo(6L);
        assertThat(todos.get(0).getRoute()).isEqualTo("/admin?section=verifications");
        assertThat(todos.get(1).getType()).isEqualTo("REPORT");
        assertThat(todos.get(1).getCount()).isEqualTo(4L);
        assertThat(todos.get(2).getType()).isEqualTo("DISPUTED_TASK");
        assertThat(todos.get(2).getCount()).isEqualTo(2L);
    }

    @Test
    void todosHandleZeroCounters() {
        when(adminDashboardMapper.countPendingVerifications()).thenReturn(0L);
        when(adminDashboardMapper.countPendingReports()).thenReturn(0L);
        when(adminDashboardMapper.countDisputedTasks()).thenReturn(0L);

        List<DashboardTodo> todos = service.getTodos();

        assertThat(todos).hasSize(3);
        assertThat(todos).allMatch((item) -> item.getCount() == 0L);
    }

    private Map<String, Object> row(String status, long count) {
        Map<String, Object> row = new HashMap<>();
        row.put("status", status);
        row.put("count", count);
        return row;
    }

    private Map<String, Object> row(LocalDate day, long count) {
        Map<String, Object> row = new HashMap<>();
        row.put("day", Date.valueOf(day));
        row.put("count", count);
        return row;
    }
}
