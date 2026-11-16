package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.dto.view.HomePostView;
import com.campusbridge.dto.view.HomeRecommendationsView;
import com.campusbridge.dto.view.HomeTaskView;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.service.RecommendationService;
import com.campusbridge.utils.AuthContextUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/home")
public class HomeController {
    private final PostMapper postMapper;
    private final TaskMapper taskMapper;
    private final RecommendationService recommendationService;
    private final AuthContextUtil authContextUtil;

    public HomeController(PostMapper postMapper,
                          TaskMapper taskMapper,
                          RecommendationService recommendationService,
                          AuthContextUtil authContextUtil) {
        this.postMapper = postMapper;
        this.taskMapper = taskMapper;
        this.recommendationService = recommendationService;
        this.authContextUtil = authContextUtil;
    }

    @GetMapping("/recommendations")
    public ApiResponse<HomeRecommendationsView> getRecommendations() {
        Long userId = authContextUtil.getCurrentUserId();
        List<PostView> posts;
        List<TaskView> tasks;

        if (userId != null) {
            posts = recommendationService.getRecommendedPosts(userId, 8);
            tasks = recommendationService.getRecommendedTasks(userId, 6);
        } else {
            posts = postMapper.selectHotPosts(8);
            tasks = taskMapper.selectRecommendedTasks("OPEN", 6);
        }

        return ApiResponse.success(new HomeRecommendationsView(
                posts.stream().map(this::toPublicPost).toList(),
                tasks.stream().map(this::toPublicTask).toList()));
    }

    private HomePostView toPublicPost(PostView post) {
        return new HomePostView(post.getId(), post.getAuthorId(), post.getAuthorName(),
                post.getCategory(), post.getTitle(), post.getContent(),
                post.getReplyCount(), post.getLikeCount(), post.getCreatedAt());
    }

    private HomeTaskView toPublicTask(TaskView task) {
        return new HomeTaskView(task.getId(), task.getRequesterId(), task.getCategory(),
                task.getTitle(), task.getDescription(), task.getCampus(), task.getLocation(),
                task.getDeliveryLocation(), task.getDeadline(), task.getRewardType(), task.getRewardDescription());
    }
}
