package com.campusbridge.service;

import com.campusbridge.pojo.Post;
import com.campusbridge.pojo.Task;

import java.util.List;
import java.util.Map;

public interface RecommendationService {
    Map<String, Object> getUserPreferences(Long userId);

    List<Task> getRecommendedTasks(Long userId, int limit);

    List<Post> getRecommendedPosts(Long userId, int limit);
}
