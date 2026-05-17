package com.campusbridge.mapper;

import com.campusbridge.pojo.TaskApplication;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TaskApplicationMapper {
    int insert(TaskApplication entity);

    TaskApplication selectById(Long id);

    List<TaskApplication> selectAll();

    int updateById(TaskApplication entity);

    int deleteById(Long id);
}
