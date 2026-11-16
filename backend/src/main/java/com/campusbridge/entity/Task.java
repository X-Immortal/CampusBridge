package com.campusbridge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tasks")
public class Task {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requesterId;
    private Long providerId;
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
