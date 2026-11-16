package com.campusbridge.mapper;

import com.campusbridge.dto.view.CategoryCount;
import com.campusbridge.dto.view.DailyCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdminDashboardMapper {

    long countAllUsers();

    long countVerifiedUsers();

    long countAllTasks();

    long countActiveTasks();

    long countCompletedTasks();

    long countAllPosts();

    long countPendingVerifications();

    long countPendingReports();

    long countDisputedTasks();

    List<DailyCount> countNewUsersByDay(@Param("from") LocalDateTime from);

    List<DailyCount> countNewTasksByDay(@Param("from") LocalDateTime from);

    List<DailyCount> countNewPostsByDay(@Param("from") LocalDateTime from);

    List<CategoryCount> countTasksByStatus();

    List<CategoryCount> countReportsByStatus();

    List<CategoryCount> countReportsByTargetType();
}
