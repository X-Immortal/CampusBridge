package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campusbridge.pojo.TaskHistory;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

import java.util.List;

@Mapper
public interface TaskHistoryMapper extends BaseMapper<TaskHistory> {

    default boolean existsReminder(
            Long taskId, String note, LocalDateTime activeSince, LocalDateTime createdBefore) {
        return selectOne(Wrappers.<TaskHistory>lambdaQuery()
                .select(TaskHistory::getId)
                .eq(TaskHistory::getTaskId, taskId)
                .eq(TaskHistory::getNote, note)
                .ge(TaskHistory::getCreatedAt, activeSince)
                .le(createdBefore != null, TaskHistory::getCreatedAt, createdBefore)
                .last("LIMIT 1")) != null;
    }

    default List<TaskHistory> selectByTaskId(Long taskId) {
        return selectList(Wrappers.<TaskHistory>lambdaQuery().eq(TaskHistory::getTaskId, taskId)
                .orderByAsc(TaskHistory::getCreatedAt, TaskHistory::getId));
    }

    default int deleteByTaskId(Long taskId) {
        return delete(Wrappers.<TaskHistory>lambdaQuery().eq(TaskHistory::getTaskId, taskId));
    }
}
