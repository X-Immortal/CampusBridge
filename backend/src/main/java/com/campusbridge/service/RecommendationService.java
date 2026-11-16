package com.campusbridge.service;

import com.campusbridge.dto.UserPreferences;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.dto.view.TaskView;

import java.util.List;

public interface RecommendationService {
    UserPreferences getUserPreferences(Long userId);

    List<TaskView> getRecommendedTasks(Long userId, int limit);

    List<PostView> getRecommendedPosts(Long userId, int limit);
}
