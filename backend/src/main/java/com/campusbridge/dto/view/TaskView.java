package com.campusbridge.dto.view;

import com.campusbridge.pojo.Task;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class TaskView {
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

    public static TaskView from(Task entity) {
        if (entity == null) {
            return null;
        }
        TaskView view = new TaskView();
        view.id = entity.getId();
        view.requesterId = entity.getRequesterId();
        view.providerId = entity.getProviderId();
        view.category = entity.getCategory();
        view.title = entity.getTitle();
        view.description = entity.getDescription();
        view.campus = entity.getCampus();
        view.location = entity.getLocation();
        view.deliveryLocation = entity.getDeliveryLocation();
        view.deadline = entity.getDeadline();
        view.rewardType = entity.getRewardType();
        view.rewardDescription = entity.getRewardDescription();
        view.rewardAmount = entity.getRewardAmount();
        view.completionStandard = entity.getCompletionStandard();
        view.requiresProof = entity.getRequiresProof();
        view.onlyInAppContact = entity.getOnlyInAppContact();
        view.allowOfflineContact = entity.getAllowOfflineContact();
        view.isInstantTask = entity.getIsInstantTask();
        view.routeHint = entity.getRouteHint();
        view.riskDisclosureConfirmed = entity.getRiskDisclosureConfirmed();
        view.sensitiveReason = entity.getSensitiveReason();
        view.status = entity.getStatus();
        view.createdAt = entity.getCreatedAt();
        view.updatedAt = entity.getUpdatedAt();
        return view;
    }
}
