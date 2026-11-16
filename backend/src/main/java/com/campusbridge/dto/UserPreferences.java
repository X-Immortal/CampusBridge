package com.campusbridge.dto;

import com.campusbridge.enums.PostCategory;
import com.campusbridge.enums.RewardType;
import com.campusbridge.enums.TaskCategory;

import java.util.List;

public record UserPreferences(List<TaskCategory> preferredTaskCategories, List<String> preferredCampuses,
        List<RewardType> preferredRewardTypes, List<PostCategory> interactionCategories) {
}
