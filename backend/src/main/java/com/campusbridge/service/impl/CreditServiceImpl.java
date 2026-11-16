package com.campusbridge.service.impl;

import com.campusbridge.enums.CreditSourceType;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.entity.CreditRecord;
import com.campusbridge.entity.Task;
import com.campusbridge.entity.UserCredit;
import com.campusbridge.enums.RewardType;
import com.campusbridge.mapper.CreditRecordMapper;
import com.campusbridge.mapper.UserCreditMapper;
import com.campusbridge.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {
    private static final int MAX_REASON_LENGTH = 255;

    private final UserCreditMapper userCreditMapper;
    private final CreditRecordMapper creditRecordMapper;

    @Override
    public void adjustCredit(
            Long userId,
            int changeValue,
            String reason,
            CreditSourceType sourceType,
            Long sourceId) {
        if (userId == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "userId must not be null");
        }
        if (changeValue == 0) {
            return;
        }

        userCreditMapper.initializeForUser(userId);
        UserCredit credit = userCreditMapper.selectByUserIdForUpdate(userId);
        LocalDateTime now = LocalDateTime.now();
        int nextPoints = credit.getPoints() + changeValue;
        credit.setPoints(nextPoints);
        credit.setLevel(calculateLevel(nextPoints));
        credit.setUpdatedAt(now);
        if (userCreditMapper.updateById(credit) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to update user credit");
        }

        CreditRecord record = new CreditRecord();
        record.setUserId(userId);
        record.setChangeValue(changeValue);
        record.setReason(abbreviate(defaultIfBlank(reason, "CREDIT_ADJUSTED"), MAX_REASON_LENGTH));
        record.setSourceType(sourceType);
        record.setSourceId(sourceId);
        record.setCreatedAt(now);

        if (creditRecordMapper.insert(record) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create credit record");
        }
    }

    @Override
    public void awardTaskReward(Task task) {
        Integer amount = task.getRewardAmount();
        if (task.getRewardType() == RewardType.POINTS && amount != null && amount > 0) {
            adjustCredit(task.getProviderId(), amount,
                    "任务 #" + task.getId() + " 积分报酬收入", CreditSourceType.TASK, task.getId());
        }
    }

    private int calculateLevel(int points) {
        if (points >= 100) {
            return 5;
        }
        if (points >= 90) {
            return 4;
        }
        if (points >= 80) {
            return 3;
        }
        if (points >= 60) {
            return 2;
        }
        return 1;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
