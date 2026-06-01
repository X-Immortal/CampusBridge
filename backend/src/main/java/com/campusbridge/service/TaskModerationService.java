package com.campusbridge.service;

import com.campusbridge.pojo.Task;

import java.util.List;

public interface TaskModerationService {
    /**
     * 查询待审核的敏感任务列表（分页）
     * @param page 页码（从1开始）
     * @param size 每页数量
     * @return 待审核任务列表
     */
    List<Task> listPendingReviewTasks(Integer page, Integer size);

    /**
     * 统计待审核任务总数
     * @return 总数
     */
    int countPendingReviewTasks();

    /**
     * 管理员审核通过任务
     * @param taskId 任务ID
     * @param adminId 管理员ID
     * @param reviewNote 审核备注（可选）
     * @return 审核后的任务
     */
    Task approveTask(Long taskId, Long adminId, String reviewNote);

    /**
     * 管理员拒绝任务
     * @param taskId 任务ID
     * @param adminId 管理员ID
     * @param rejectReason 拒绝原因（必填）
     * @return 拒绝后的任务
     */
    Task rejectTask(Long taskId, Long adminId, String rejectReason);
}
