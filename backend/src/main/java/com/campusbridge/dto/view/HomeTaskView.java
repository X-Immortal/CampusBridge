package com.campusbridge.dto.view;

import java.time.LocalDateTime;

public record HomeTaskView(Long id, Long requesterId, String category, String title, String description,
        String campus, String location, String deliveryLocation, LocalDateTime deadline,
        String rewardType, String rewardDescription) {
}
