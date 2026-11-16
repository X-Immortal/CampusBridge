package com.campusbridge.dto.view;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTrend {
    private List<String> days;
    private List<Long> newUsers;
    private List<Long> newTasks;
    private List<Long> newPosts;
}
