package com.campusbridge.service.impl;

import com.campusbridge.common.ErrorCode;
import com.campusbridge.common.BusinessException;
import com.campusbridge.mapper.RewardSettlementMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.mapper.UserCreditMapper;
import com.campusbridge.pojo.RewardSettlement;
import com.campusbridge.pojo.Task;
import com.campusbridge.pojo.UserCredit;
import com.campusbridge.service.RewardSettlementService;
import com.campusbridge.service.UserCreditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class RewardSettlementServiceImpl implements RewardSettlementService {

    private static final String REWARD_TYPE_POINTS = "POINTS";
    private static final String REWARD_TYPE_NEGOTIABLE = "NEGOTIABLE";
    private static final String SETTLEMENT_STATUS_COMPLETED = "COMPLETED";
    private static final int DEFAULT_POINTS_AMOUNT = 10;

    private final RewardSettlementMapper rewardSettlementMapper;
    private final TaskMapper taskMapper;
    private final UserCreditMapper userCreditMapper;
    private final UserCreditService userCreditService;

    public RewardSettlementServiceImpl(
            RewardSettlementMapper rewardSettlementMapper,
            TaskMapper taskMapper,
            UserCreditMapper userCreditMapper,
            UserCreditService userCreditService) {
        this.rewardSettlementMapper = rewardSettlementMapper;
        this.taskMapper = taskMapper;
        this.userCreditMapper = userCreditMapper;
        this.userCreditService = userCreditService;
    }

    @Override
    public void settleTaskReward(Long taskId, Long requesterId, Long providerId) {
        requireTaskId(taskId);
        requireUserId(requesterId, "requesterId");
        requireUserId(providerId, "providerId");

        // 检查是否已经结算过
        RewardSettlement existing = rewardSettlementMapper.selectByTaskId(taskId);
        if (existing != null) {
            throw new BusinessException(ErrorCode.COMMON_003_CONFLICT,
                    "Task reward already settled");
        }

        // 获取任务信息
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        // 根据奖励类型处理结算
        String rewardType = task.getRewardType();
        if (REWARD_TYPE_POINTS.equals(rewardType)) {
            settlePointsReward(taskId, requesterId, providerId);
        } else if (REWARD_TYPE_NEGOTIABLE.equals(rewardType)) {
            settleNegotiableReward(taskId, requesterId, providerId, task);
        }
        // FREE 类型不需要结算
    }

    @Override
    public RewardSettlement getSettlementByTaskId(Long taskId) {
        requireTaskId(taskId);
        return rewardSettlementMapper.selectByTaskId(taskId);
    }

    @Override
    public List<RewardSettlement> getUserSettlements(Long userId, Integer page, Integer size) {
        requireUserId(userId, "userId");
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 20;
        }
        int offset = (page - 1) * size;
        return rewardSettlementMapper.selectByUserId(userId, offset, size);
    }

    @Override
    public int countUserSettlements(Long userId) {
        requireUserId(userId, "userId");
        return rewardSettlementMapper.countByUserId(userId);
    }

    private void settlePointsReward(Long taskId, Long requesterId, Long providerId) {
        LocalDateTime now = LocalDateTime.now();

        // 从求助者扣除积分
        UserCredit requesterCredit = userCreditMapper.selectByUserId(requesterId);
        if (requesterCredit == null) {
            requesterCredit = createOrUpdateUserCredit(requesterId, 0);
        }

        int pointsToTransfer = DEFAULT_POINTS_AMOUNT;
        if (requesterCredit.getPoints() < pointsToTransfer) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "Insufficient points for reward settlement");
        }

        requesterCredit.setPoints(requesterCredit.getPoints() - pointsToTransfer);
        requesterCredit.setUpdatedAt(now);
        userCreditMapper.updateById(requesterCredit);

        // 给帮助者增加积分
        UserCredit providerCredit = userCreditMapper.selectByUserId(providerId);
        if (providerCredit == null) {
            providerCredit = createOrUpdateUserCredit(providerId, 0);
        }
        providerCredit.setPoints(providerCredit.getPoints() + pointsToTransfer);
        providerCredit.setUpdatedAt(now);
        userCreditMapper.updateById(providerCredit);

        // 记录积分变化
        userCreditService.updateCreditByTaskCompletion(requesterId, taskId);

        // 创建结算记录
        RewardSettlement settlement = new RewardSettlement();
        settlement.setTaskId(taskId);
        settlement.setRequesterId(requesterId);
        settlement.setProviderId(providerId);
        settlement.setRewardType(REWARD_TYPE_POINTS);
        settlement.setPointsAmount(pointsToTransfer);
        settlement.setSettlementStatus(SETTLEMENT_STATUS_COMPLETED);
        settlement.setSettledAt(now);
        settlement.setCreatedAt(now);

        int affectedRows = rewardSettlementMapper.insert(settlement);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create reward settlement");
        }
    }

    private void settleNegotiableReward(Long taskId, Long requesterId, Long providerId, Task task) {
        LocalDateTime now = LocalDateTime.now();

        // 协商报酬暂时记录，实际支付由线下完成
        // 这里只记录协商信息，不处理实际的金额转移
        BigDecimal negotiatedAmount = parseNegotiatedAmount(task.getRewardDescription());

        RewardSettlement settlement = new RewardSettlement();
        settlement.setTaskId(taskId);
        settlement.setRequesterId(requesterId);
        settlement.setProviderId(providerId);
        settlement.setRewardType(REWARD_TYPE_NEGOTIABLE);
        settlement.setNegotiatedAmount(negotiatedAmount);
        settlement.setSettlementStatus(SETTLEMENT_STATUS_COMPLETED);
        settlement.setSettledAt(now);
        settlement.setCreatedAt(now);

        int affectedRows = rewardSettlementMapper.insert(settlement);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create reward settlement");
        }
    }

    private UserCredit createOrUpdateUserCredit(Long userId, int initialPoints) {
        LocalDateTime now = LocalDateTime.now();
        UserCredit credit = userCreditMapper.selectByUserId(userId);
        if (credit == null) {
            credit = new UserCredit();
            credit.setUserId(userId);
            credit.setPoints(initialPoints);
            credit.setLevel(1);
            credit.setCreatedAt(now);
            credit.setUpdatedAt(now);
            userCreditMapper.insert(credit);
        }
        return credit;
    }

    private BigDecimal parseNegotiatedAmount(String rewardDescription) {
        if (rewardDescription == null || rewardDescription.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            // 尝试从描述中提取数字
            String numericPart = rewardDescription.replaceAll("[^0-9.]", "");
            if (numericPart.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(numericPart);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private void requireTaskId(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId is required");
        }
    }

    private void requireUserId(Long userId, String paramName) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    paramName + " is required");
        }
    }
}
