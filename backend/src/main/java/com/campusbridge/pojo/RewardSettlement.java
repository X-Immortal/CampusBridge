package com.campusbridge.pojo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RewardSettlement {
    private Long id;
    private Long taskId;
    private Long requesterId;
    private Long providerId;
    private String rewardType;
    private Integer pointsAmount;
    private BigDecimal negotiatedAmount;
    private String settlementStatus;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
}
