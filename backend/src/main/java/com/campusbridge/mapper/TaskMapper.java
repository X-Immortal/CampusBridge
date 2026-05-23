package com.campusbridge.mapper;

import com.campusbridge.pojo.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

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
}
