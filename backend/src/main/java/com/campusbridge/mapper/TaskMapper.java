package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusbridge.dto.query.TaskQuery;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.entity.Task;
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

    default int updateStatusById(Long id, String fromStatus, String toStatus, LocalDateTime updatedAt) {
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
                .set(Task::getStatus, "ACCEPTED")
                .set(Task::getUpdatedAt, updatedAt)
                .eq(Task::getId, id)
                .eq(Task::getRequesterId, requesterId)
                .eq(Task::getStatus, "OPEN")
                .isNull(Task::getProviderId));
    }

    default int releaseProviderById(
            Long id, Long providerId, String fromStatus, LocalDateTime updatedAt) {
        return update(null, Wrappers.<Task>lambdaUpdate()
                .set(Task::getProviderId, null)
                .set(Task::getStatus, "OPEN")
                .set(Task::getUpdatedAt, updatedAt)
                .eq(Task::getId, id)
                .eq(Task::getProviderId, providerId)
                .eq(Task::getStatus, fromStatus));
    }

    Page<TaskView> selectWithFilters(Page<TaskView> page, @Param("query") TaskQuery query);

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

    List<TaskView> selectRecommendedTasks(
            @Param("status") String status,
            @Param("size") Integer size);

    List<String> selectPreferredCampuses(
            @Param("userId") Long userId,
            @Param("limit") int limit);

    List<TaskView> selectOpenTasksWithDeadline();
}
