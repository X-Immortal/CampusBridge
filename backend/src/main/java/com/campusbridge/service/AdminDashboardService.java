package com.campusbridge.service;

import com.campusbridge.dto.view.DashboardDistributionItem;
import com.campusbridge.dto.view.DashboardSummary;
import com.campusbridge.dto.view.DashboardTodo;
import com.campusbridge.dto.view.DashboardTrend;

import java.util.List;

public interface AdminDashboardService {

    DashboardSummary getSummary();

    DashboardTrend getTrends(int days);

    List<DashboardDistributionItem> getTaskStatusStats();

    List<DashboardDistributionItem> getReportStatusStats();

    List<DashboardDistributionItem> getReportTargetTypeStats();

    List<DashboardTodo> getTodos();
}
