package com.campusbridge.service;

import com.campusbridge.pojo.CreditRecord;
import com.campusbridge.pojo.UserCredit;

import java.util.List;

public interface UserCreditService {
    /**
     * 获取用户信用信息
     * @param userId 用户ID
     * @return 用户信用信息
     */
    UserCredit getUserCredit(Long userId);

    /**
     * 获取用户信用分变化记录
     * @param userId 用户ID
     * @param page 页码（从1开始）
     * @param size 每页数量
     * @return 信用分变化记录列表
     */
    List<CreditRecord> getCreditRecords(Long userId, Integer page, Integer size);

    /**
     * 根据评价更新信用分
     * @param revieweeId 被评价者ID
     * @param rating 评分（1-5）
     * @param taskId 任务ID
     */
    void updateCreditByReview(Long revieweeId, Integer rating, Long taskId);

    /**
     * 根据任务完成更新信用分
     * @param userId 用户ID
     * @param taskId 任务ID
     */
    void updateCreditByTaskCompletion(Long userId, Long taskId);

    /**
     * 根据举报处理结果更新信用分
     * @param userId 用户ID
     * @param scoreChange 分数变化（负数表示扣分）
     * @param reason 原因
     * @param reportId 举报ID
     */
    void updateCreditByReport(Long userId, Integer scoreChange, String reason, Long reportId);
}
