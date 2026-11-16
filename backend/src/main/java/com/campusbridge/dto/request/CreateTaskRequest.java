package com.campusbridge.dto.request;

import com.campusbridge.enums.RewardType;
import com.campusbridge.enums.TaskCategory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequest {
    public TaskCategory category;
    public String title;
    public String description;
    public String campus;
    public String location;
    public String deliveryLocation;
    public LocalDateTime deadline;
    public RewardType rewardType;
    public String rewardDescription;
    public String completionStandard;
    public Boolean requiresProof;
    public Boolean onlyInAppContact;
    public Boolean allowOfflineContact;
    public Boolean isInstantTask;
    public String routeHint;
    public Boolean riskDisclosureConfirmed;
    public String sensitiveReason;
    public Integer rewardAmount;
    public List<Long> attachmentIds;
}
