package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusbridge.dto.query.TaskQuery;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.entity.Task;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.enums.TaskHistoryEventType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    TaskView selectDetailById(Long id);

    default Task selectByIdForUpdate(Long id) {
        return selectOne(Wrappers.<Task>lambdaQuery().eq(Task::getId, id).last("FOR UPDATE"));
    }

    default int updateStatusById(Long id, TaskStatus fromStatus, TaskStatus toStatus, LocalDateTime updatedAt) {
        return update(null, Wrappers.<Task>lambdaUpdate()
                .set(Task::getStatus, toStatus)
                .set(Task::getUpdatedAt, updatedAt)
                .eq(Task::getId, id)
                .eq(Task::getStatus, fromStatus));
    }

    default int acceptProviderById(
            Long id, Long requesterId, Long providerId, LocalDateTime updatedAt) {
        return update(null, Wrappers.<Task>lambdaUpdate()
                .set(Task::getProviderId, providerId)
                .set(Task::getStatus, TaskStatus.ACCEPTED)
                .set(Task::getUpdatedAt, updatedAt)
                .eq(Task::getId, id)
                .eq(Task::getRequesterId, requesterId)
                .eq(Task::getStatus, TaskStatus.OPEN)
                .isNull(Task::getProviderId));
    }

    default int releaseProviderById(
            Long id, Long providerId, TaskStatus fromStatus, LocalDateTime updatedAt) {
        return update(null, Wrappers.<Task>lambdaUpdate()
                .set(Task::getProviderId, null)
                .set(Task::getStatus, TaskStatus.OPEN)
                .set(Task::getUpdatedAt, updatedAt)
                .eq(Task::getId, id)
                .eq(Task::getProviderId, providerId)
                .eq(Task::getStatus, fromStatus));
    }

    Page<TaskView> selectWithFilters(Page<TaskView> page, @Param("query") TaskQuery query);

    List<Task> selectOpenTasksNeedingTimeoutReminder(
            @Param("referenceTime") LocalDateTime referenceTime,
            @Param("reminderEventType") TaskHistoryEventType reminderEventType,
            @Param("limit") Integer limit);

    List<Task> selectUnconfirmedTasksNeedingTimeoutReminder(
            @Param("referenceTime") LocalDateTime referenceTime,
            @Param("reminderEventType") TaskHistoryEventType reminderEventType,
            @Param("limit") Integer limit);

    List<Task> selectTasksReadyForTimeoutTransition(
            @Param("status") TaskStatus status,
            @Param("reminderEventType") TaskHistoryEventType reminderEventType,
            @Param("reminderCreatedBefore") LocalDateTime reminderCreatedBefore,
            @Param("limit") Integer limit);

    List<TaskView> selectRecommendedTasks(
            @Param("status") TaskStatus status,
            @Param("size") Integer size);

    List<String> selectPreferredCampuses(
            @Param("userId") Long userId,
            @Param("limit") int limit);

    List<TaskView> selectOpenTasksWithDeadline();
}
