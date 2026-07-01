package com.campusbridge.service;

import com.campusbridge.dto.view.ViewDtos.DashboardDistributionItem;
import com.campusbridge.dto.view.ViewDtos.DashboardSummary;
import com.campusbridge.dto.view.ViewDtos.DashboardTodo;
import com.campusbridge.dto.view.ViewDtos.DashboardTrend;

import java.util.List;

public interface AdminDashboardService {

    DashboardSummary getSummary();

    DashboardTrend getTrends(int days);

    List<DashboardDistributionItem> getTaskStatusStats();

    List<DashboardDistributionItem> getReportStatusStats();

    List<DashboardDistributionItem> getReportTargetTypeStats();

    List<DashboardTodo> getTodos();
}
