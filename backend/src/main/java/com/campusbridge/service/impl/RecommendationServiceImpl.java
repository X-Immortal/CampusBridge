package com.campusbridge.service.impl;

import com.campusbridge.dto.UserPreferences;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.enums.PostCategory;
import com.campusbridge.enums.RewardType;
import com.campusbridge.enums.TaskCategory;
import com.campusbridge.mapper.PostInteractionMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.TaskApplicationMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.service.RecommendationService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {
    private final TaskMapper taskMapper;
    private final PostMapper postMapper;
    private final TaskApplicationMapper taskApplicationMapper;
    private final PostInteractionMapper postInteractionMapper;

    @Override
    public UserPreferences getUserPreferences(Long userId) {
        if (userId == null) {
            return new UserPreferences(List.of(), List.of(), List.of(), List.of());
        }

        // 1. 统计用户最常接的任务分类（前3）
        List<TaskCategory> preferredTaskCategories = taskApplicationMapper.selectPreferredCategories(userId, 3);

        // 2. 统计用户最活跃的校区（前2）
        List<String> preferredCampuses = taskMapper.selectPreferredCampuses(userId, 2);

        // 3. 统计用户偏好的报酬类型（前2）
        List<RewardType> preferredRewardTypes = taskApplicationMapper.selectPreferredRewardTypes(userId, 2);

        // 4. 统计用户互动的帖子分类（前3）
        List<PostCategory> interactionCategories = postInteractionMapper.selectPreferredPostCategories(userId, 3);

        return new UserPreferences(preferredTaskCategories, preferredCampuses,
                preferredRewardTypes, interactionCategories);
    }

    @Override
    public List<TaskView> getRecommendedTasks(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return Collections.emptyList();
        }

        // 获取用户偏好
        UserPreferences preferences = getUserPreferences(userId);
        List<TaskCategory> preferredCategories = preferences.preferredTaskCategories();
        List<String> preferredCampuses = preferences.preferredCampuses();
        List<RewardType> preferredRewards = preferences.preferredRewardTypes();

        // 获取所有 OPEN 状态且未截止的任务
        List<TaskView> candidates = taskMapper.selectOpenTasksWithDeadline();
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 计算每个任务的推荐分并排序
        List<TaskScore> scoredTasks = candidates.stream()
                .map(task -> new TaskScore(task,
                        calculateTaskScore(task, preferredCategories,
                                preferredCampuses, preferredRewards)))
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .limit(limit)
                .collect(Collectors.toList());

        return scoredTasks.stream().map(ts -> ts.task).collect(Collectors.toList());
    }

    @Override
    public List<PostView> getRecommendedPosts(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return Collections.emptyList();
        }

        // 获取用户偏好
        UserPreferences preferences = getUserPreferences(userId);
        List<PostCategory> interactionCategories = preferences.interactionCategories();

        // 获取所有已发布的帖子
        List<PostView> candidates = postMapper.selectAllPublishedPosts();
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 计算每个帖子的推荐分并排序
        List<PostScore> scoredPosts = candidates.stream()
                .map(post -> new PostScore(post, calculatePostScore(post, interactionCategories)))
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .limit(limit)
                .collect(Collectors.toList());

        return scoredPosts.stream().map(ps -> ps.post).collect(Collectors.toList());
    }

    private int calculateTaskScore(TaskView task, List<TaskCategory> preferredCategories,
                                    List<String> preferredCampuses, List<RewardType> preferredRewards) {
        int score = 0;

        // 1. 分类匹配 (0-5分)
        if (task.getCategory() != null && !preferredCategories.isEmpty()) {
            int categoryIndex = preferredCategories.indexOf(task.getCategory());
            if (categoryIndex >= 0) {
                score += (categoryIndex < 3) ? 5 : 3;
            }
        }

        // 2. 校区匹配 (0-3分)
        if (task.getCampus() != null && !preferredCampuses.isEmpty()) {
            if (preferredCampuses.contains(task.getCampus())) {
                score += 3;
            }
        }

        // 3. 报酬偏好匹配 (0-2分)
        if (task.getRewardType() != null && !preferredRewards.isEmpty()) {
            if (preferredRewards.contains(task.getRewardType())) {
                score += 2;
            }
        }

        // 4. 热度衰减分 (0-10分)
        if (task.getCreatedAt() != null) {
            long hoursSinceCreated = Duration.between(
                    task.getCreatedAt(), LocalDateTime.now()).toHours();
            double heatScore = 10.0 / Math.max(hoursSinceCreated + 2, 2);
            score += (int) (heatScore * 10);
        }

        // 5. 时效性分 (0-5分)
        if (task.getDeadline() != null) {
            long daysUntilDeadline = Duration.between(
                    LocalDateTime.now(), task.getDeadline()).toDays();
            if (daysUntilDeadline > 0 && daysUntilDeadline <= 10) {
                score += (int) (10 - daysUntilDeadline);
            }
        }

        return score;
    }

    private int calculatePostScore(PostView post, List<PostCategory> interactionCategories) {
        int score = 0;

        // 1. 分类匹配 (0-5分)
        if (post.getCategory() != null && !interactionCategories.isEmpty()) {
            int categoryIndex = interactionCategories.indexOf(post.getCategory());
            if (categoryIndex >= 0) {
                score += (categoryIndex < 3) ? 5 : 3;
            }
        }

        // 2. 热度衰减分 (0-10分)
        if (post.getCreatedAt() != null) {
            long hoursSinceCreated = Duration.between(
                    post.getCreatedAt(), LocalDateTime.now()).toHours();
            int interactions = (post.getReplyCount() != null ? post.getReplyCount() : 0) +
                    (post.getLikeCount() != null ? post.getLikeCount() : 0) +
                    (post.getMarkCount() != null ? post.getMarkCount() : 0);
            double heatScore = (interactions + 1) / Math.max(hoursSinceCreated + 2, 2);
            score += (int) (heatScore * 10);
        }

        return score;
    }

    @AllArgsConstructor(access = AccessLevel.PACKAGE)
    private static class TaskScore {
        TaskView task;
        int score;
    }

    @AllArgsConstructor(access = AccessLevel.PACKAGE)
    private static class PostScore {
        PostView post;
        int score;
    }
}
