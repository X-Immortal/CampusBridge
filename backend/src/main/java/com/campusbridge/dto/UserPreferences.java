package com.campusbridge.dto;

import java.util.List;

public record UserPreferences(List<String> preferredTaskCategories, List<String> preferredCampuses,
        List<String> preferredRewardTypes, List<String> interactionCategories) {
}
