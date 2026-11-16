package com.campusbridge.service;

import com.campusbridge.pojo.Task;

public interface CreditService {
    void awardTaskReward(Task task);

    void adjustCredit(
            Long userId,
            int changeValue,
            String reason,
            String sourceType,
            Long sourceId);
}
