package com.campusbridge.mapper;

import com.campusbridge.pojo.TaskHistory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TaskHistoryMapper {
    int insert(TaskHistory entity);

    TaskHistory selectById(Long id);

    List<TaskHistory> selectAll();

    int updateById(TaskHistory entity);

    int deleteById(Long id);
}
