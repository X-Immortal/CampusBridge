package com.campusbridge.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    List<Map<String, Object>> countNewUsersByDay(@Param("from") LocalDateTime from);

    List<Map<String, Object>> countNewTasksByDay(@Param("from") LocalDateTime from);

    List<Map<String, Object>> countNewPostsByDay(@Param("from") LocalDateTime from);

    List<Map<String, Object>> countTasksByStatus();

    List<Map<String, Object>> countReportsByStatus();

    List<Map<String, Object>> countReportsByTargetType();
}
