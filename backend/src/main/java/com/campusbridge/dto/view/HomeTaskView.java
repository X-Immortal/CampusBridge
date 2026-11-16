package com.campusbridge.dto.view;

import com.campusbridge.enums.RewardType;
import com.campusbridge.enums.TaskCategory;

import java.time.LocalDateTime;

public record HomeTaskView(Long id, Long requesterId, TaskCategory category, String title, String description,
        String campus, String location, String deliveryLocation, LocalDateTime deadline,
        RewardType rewardType, String rewardDescription) {
}
