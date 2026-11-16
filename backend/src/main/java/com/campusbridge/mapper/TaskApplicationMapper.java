package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campusbridge.entity.TaskApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskApplicationMapper extends BaseMapper<TaskApplication> {

    default TaskApplication selectByTaskIdAndApplicantId(
            Long taskId,
            Long applicantId) {
        return selectOne(Wrappers.<TaskApplication>lambdaQuery()
                .eq(TaskApplication::getTaskId, taskId).eq(TaskApplication::getApplicantId, applicantId));
    }

    default List<TaskApplication> selectByTaskId(Long taskId) {
        return selectList(Wrappers.<TaskApplication>lambdaQuery().eq(TaskApplication::getTaskId, taskId)
                .orderByDesc(TaskApplication::getCreatedAt, TaskApplication::getId));
    }

    default List<TaskApplication> selectByApplicantId(Long applicantId) {
        return selectList(Wrappers.<TaskApplication>lambdaQuery().eq(TaskApplication::getApplicantId, applicantId)
                .orderByDesc(TaskApplication::getCreatedAt, TaskApplication::getId));
    }

    default int updateStatusById(Long id, String fromStatus, String toStatus, LocalDateTime handledAt) {
        return update(null, Wrappers.<TaskApplication>lambdaUpdate()
                .set(TaskApplication::getStatus, toStatus)
                .set(TaskApplication::getHandledAt, handledAt)
                .eq(TaskApplication::getId, id)
                .eq(TaskApplication::getStatus, fromStatus));
    }

    default int rejectOtherPendingByTaskId(Long taskId, Long acceptedApplicationId, LocalDateTime handledAt) {
        return update(null, Wrappers.<TaskApplication>lambdaUpdate()
                .set(TaskApplication::getStatus, "REJECTED")
                .set(TaskApplication::getHandledAt, handledAt)
                .eq(TaskApplication::getTaskId, taskId)
                .ne(TaskApplication::getId, acceptedApplicationId)
                .eq(TaskApplication::getStatus, "PENDING"));
    }

    List<String> selectPreferredCategories(
            @Param("userId") Long userId,
            @Param("limit") int limit);

    List<String> selectPreferredRewardTypes(
            @Param("userId") Long userId,
            @Param("limit") int limit);
}
