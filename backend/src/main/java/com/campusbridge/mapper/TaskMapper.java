package com.campusbridge.mapper;

import com.campusbridge.pojo.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface TaskMapper {
    int insert(Task entity);

    Task selectById(Long id);

    List<Task> selectAll();

    int updateById(Task entity);

    int deleteById(Long id);

    int updateStatusById(
            @Param("id") Long id,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("updatedAt") LocalDateTime updatedAt);

    int acceptProviderById(
            @Param("id") Long id,
            @Param("requesterId") Long requesterId,
            @Param("providerId") Long providerId,
            @Param("updatedAt") LocalDateTime updatedAt);

    List<Task> selectWithFilters(Map<String, Object> params);

    List<Task> selectTasksByStatus(
            @Param("status") String status,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit);

    int countTasksByStatus(@Param("status") String status);

    List<Task> selectTasksNeedingTimeoutReminder(
            @Param("status") String status,
            @Param("referenceTime") LocalDateTime referenceTime,
            @Param("reminderNote") String reminderNote,
            @Param("limit") Integer limit);

    List<Task> selectTasksReadyForTimeoutTransition(
            @Param("status") String status,
            @Param("reminderNote") String reminderNote,
            @Param("reminderCreatedBefore") LocalDateTime reminderCreatedBefore,
            @Param("limit") Integer limit);

    List<Task> selectRecommendedTasks(
            @Param("status") String status,
            @Param("size") Integer size);
}
