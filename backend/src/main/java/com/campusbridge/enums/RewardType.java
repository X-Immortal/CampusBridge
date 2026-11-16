package com.campusbridge.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RewardType {
    FREE(null), POINTS(10), CASH(null), NEGOTIABLE(null);

    private final Integer maxAmount;

    public boolean isAmountWithinLimit(Integer amount) {
        return amount == null || maxAmount == null || amount <= maxAmount;
    }
}
