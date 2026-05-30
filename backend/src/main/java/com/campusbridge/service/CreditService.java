package com.campusbridge.service;

public interface CreditService {
    void adjustCredit(
            Long userId,
            int changeValue,
            String reason,
            String sourceType,
            Long sourceId);
}
