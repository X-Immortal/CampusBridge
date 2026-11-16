package com.campusbridge.dto.view;

import java.util.List;

public record HomeRecommendationsView(List<HomePostView> hotPosts, List<HomeTaskView> latestTasks) {
}
