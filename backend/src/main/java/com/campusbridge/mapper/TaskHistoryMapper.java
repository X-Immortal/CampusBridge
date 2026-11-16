package com.campusbridge.mapper;

import com.campusbridge.pojo.TaskHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

import java.util.List;

@Mapper
public interface TaskHistoryMapper {
    int insert(TaskHistory entity);

    boolean existsReminder(
            @Param("taskId") Long taskId,
            @Param("note") String note,
            @Param("activeSince") LocalDateTime activeSince,
            @Param("createdBefore") LocalDateTime createdBefore);

    TaskHistory selectById(Long id);

    List<TaskHistory> selectAll();

    List<TaskHistory> selectByTaskId(Long taskId);

    int updateById(TaskHistory entity);

    int deleteById(Long id);

    int deleteByTaskId(Long taskId);
}
