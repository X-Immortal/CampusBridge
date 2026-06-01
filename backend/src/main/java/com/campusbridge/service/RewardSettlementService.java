package com.campusbridge.service;

import com.campusbridge.pojo.RewardSettlement;

import java.util.List;

public interface RewardSettlementService {
    /**
     * 任务完成时自动结算奖励
     * @param taskId 任务ID
     * @param requesterId 求助者ID
     * @param providerId 帮助者ID
     */
    void settleTaskReward(Long taskId, Long requesterId, Long providerId);

    /**
     * 查询任务的结算记录
     * @param taskId 任务ID
     * @return 结算记录
     */
    RewardSettlement getSettlementByTaskId(Long taskId);

    /**
     * 查询用户的积分流水记录（分页）
     * @param userId 用户ID
     * @param page 页码（从1开始）
     * @param size 每页数量
     * @return 积分流水记录列表
     */
    List<RewardSettlement> getUserSettlements(Long userId, Integer page, Integer size);

    /**
     * 统计用户的结算记录总数
     * @param userId 用户ID
     * @return 总数
     */
    int countUserSettlements(Long userId);
}
