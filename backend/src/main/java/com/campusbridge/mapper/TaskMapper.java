package com.campusbridge.mapper;

import com.campusbridge.pojo.Task;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TaskMapper {
    int insert(Task entity);

    Task selectById(Long id);

    List<Task> selectAll();

    int updateById(Task entity);

    int deleteById(Long id);
}
