package com.campusbridge.service;

import com.campusbridge.enums.CreditSourceType;
import com.campusbridge.entity.Task;

public interface CreditService {
    void awardTaskReward(Task task);

    void adjustCredit(
            Long userId,
            int changeValue,
            String reason,
            CreditSourceType sourceType,
            Long sourceId);
}
