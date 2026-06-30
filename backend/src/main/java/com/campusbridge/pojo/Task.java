package com.campusbridge.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Task {
    private Long id;
    private Long requesterId;
    private Long providerId;
    private String requesterName;
    private String providerName;
    private String category;
    private String title;
    private String description;
    private String campus;
    private String location;
    private String deliveryLocation;
    private LocalDateTime deadline;
    private String rewardType;
    private String rewardDescription;
    private Integer rewardAmount;
    private String completionStandard;
    private Integer requiresProof;
    private Integer onlyInAppContact;
    private Integer allowOfflineContact;
    private Integer isInstantTask;
    private String routeHint;
    private Integer riskDisclosureConfirmed;
    private String sensitiveReason;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
