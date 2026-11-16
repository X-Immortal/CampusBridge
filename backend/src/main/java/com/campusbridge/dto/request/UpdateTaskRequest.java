package com.campusbridge.dto.request;

import com.campusbridge.enums.RewardType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskRequest {
    public String title;
    public String description;
    public LocalDateTime deadline;
    public RewardType rewardType;
    public String rewardDescription;
    public Integer rewardAmount;
    public String completionStandard;
    public Boolean requiresProof;
    public Boolean onlyInAppContact;
    public Boolean allowOfflineContact;
}
