package com.campusbridge.dto.request;

import com.campusbridge.enums.RewardType;
import com.campusbridge.enums.TaskCategory;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveTaskDraftRequest {
    public TaskCategory category;

    @Size(max = 100, message = "title is too long")
    public String title;

    @Size(max = 2000, message = "description is too long")
    public String description;

    @Size(max = 50, message = "campus is too long")
    public String campus;

    @Size(max = 120, message = "location is too long")
    public String location;

    @Size(max = 120, message = "deliveryLocation is too long")
    public String deliveryLocation;

    public LocalDateTime deadline;

    public RewardType rewardType;

    @Size(max = 200, message = "rewardDescription is too long")
    public String rewardDescription;

    @Size(max = 500, message = "completionStandard is too long")
    public String completionStandard;

    public Boolean requiresProof;

    public Boolean onlyInAppContact;

    public Boolean allowOfflineContact;

    public Boolean isInstantTask;

    @Size(max = 300, message = "routeHint is too long")
    public String routeHint;

    public Boolean riskDisclosureConfirmed;

    @Size(max = 300, message = "sensitiveReason is too long")
    public String sensitiveReason;

    @Positive(message = "rewardAmount must be positive")
    public Integer rewardAmount;

    @Size(max = 0, message = "Task publication attachments are not supported")
    public List<Long> attachmentIds;
}
