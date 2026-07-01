package com.campusbridge.mapper;

import com.campusbridge.pojo.TaskApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskApplicationMapper {
    int insert(TaskApplication entity);

    TaskApplication selectById(Long id);

    TaskApplication selectByTaskIdAndApplicantId(
            @Param("taskId") Long taskId,
            @Param("applicantId") Long applicantId);

    List<TaskApplication> selectByTaskId(Long taskId);

    List<TaskApplication> selectByApplicantId(Long applicantId);

    List<TaskApplication> selectAll();

    int updateById(TaskApplication entity);

    int updateStatusById(
            @Param("id") Long id,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("handledAt") LocalDateTime handledAt);

    int rejectOtherPendingByTaskId(
            @Param("taskId") Long taskId,
            @Param("acceptedApplicationId") Long acceptedApplicationId,
            @Param("handledAt") LocalDateTime handledAt);

    int deleteById(Long id);
}
