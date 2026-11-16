package com.campusbridge.service;

import com.campusbridge.dto.view.PostView;
import com.campusbridge.dto.view.TaskView;

import java.util.List;
import java.util.Map;

public interface RecommendationService {
    Map<String, Object> getUserPreferences(Long userId);

    List<TaskView> getRecommendedTasks(Long userId, int limit);

    List<PostView> getRecommendedPosts(Long userId, int limit);
}
