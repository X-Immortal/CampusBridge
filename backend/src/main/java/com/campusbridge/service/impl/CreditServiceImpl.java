package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.mapper.CreditRecordMapper;
import com.campusbridge.mapper.UserCreditMapper;
import com.campusbridge.pojo.CreditRecord;
import com.campusbridge.pojo.UserCredit;
import com.campusbridge.service.CreditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
@Service
public class CreditServiceImpl implements CreditService {
    private static final int MAX_REASON_LENGTH = 255;
    private static final int MAX_SOURCE_TYPE_LENGTH = 50;

    private final UserCreditMapper userCreditMapper;
    private final CreditRecordMapper creditRecordMapper;

    public CreditServiceImpl(
            UserCreditMapper userCreditMapper,
            CreditRecordMapper creditRecordMapper) {
        this.userCreditMapper = userCreditMapper;
        this.creditRecordMapper = creditRecordMapper;
    }

    @Override
    public void adjustCredit(
            Long userId,
            int changeValue,
            String reason,
            String sourceType,
            Long sourceId) {
        if (userId == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "userId must not be null");
        }
        if (changeValue == 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        UserCredit credit = userCreditMapper.selectByUserId(userId);
        if (credit == null) {
            credit = new UserCredit();
            credit.setUserId(userId);
            credit.setPoints(changeValue);
            credit.setLevel(calculateLevel(changeValue));
            credit.setUpdatedAt(now);
            if (userCreditMapper.insert(credit) != 1) {
                throw new BusinessException(
                        ErrorCode.COMMON_999_INTERNAL_ERROR,
                        "Failed to create user credit");
            }
        } else {
            int currentPoints = credit.getPoints() == null ? 0 : credit.getPoints();
            int nextPoints = currentPoints + changeValue;
            credit.setPoints(nextPoints);
            credit.setLevel(calculateLevel(nextPoints));
            credit.setUpdatedAt(now);
            if (userCreditMapper.updateById(credit) != 1) {
                throw new BusinessException(
                        ErrorCode.COMMON_999_INTERNAL_ERROR,
                        "Failed to update user credit");
            }
        }

        CreditRecord record = new CreditRecord();
        record.setUserId(userId);
        record.setChangeValue(changeValue);
        record.setReason(abbreviate(defaultIfBlank(reason, "CREDIT_ADJUSTED"), MAX_REASON_LENGTH));
        record.setSourceType(abbreviate(defaultIfBlank(sourceType, "SYSTEM"), MAX_SOURCE_TYPE_LENGTH));
        record.setSourceId(sourceId);
        record.setCreatedAt(now);

        if (creditRecordMapper.insert(record) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create credit record");
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
