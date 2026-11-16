package com.campusbridge.controller;

import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.pojo.Result;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.service.RecommendationService;
import com.campusbridge.utils.AuthContextUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public Result getRecommendations() {
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

        Map<String, Object> data = new HashMap<>();
        data.put("hotPosts", posts.stream().map(this::toPublicPost).toList());
        data.put("latestTasks", tasks.stream().map(this::toPublicTask).toList());
        return Result.success(data);
    }

    private Map<String, Object> toPublicPost(PostView post) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", post.getId());
        item.put("authorId", post.getAuthorId());
        item.put("authorName", post.getAuthorName());
        item.put("category", post.getCategory());
        item.put("title", post.getTitle());
        item.put("content", post.getContent());
        item.put("replyCount", post.getReplyCount());
        item.put("likeCount", post.getLikeCount());
        item.put("createdAt", post.getCreatedAt());
        return item;
    }

    private Map<String, Object> toPublicTask(TaskView task) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", task.getId());
        item.put("requesterId", task.getRequesterId());
        item.put("category", task.getCategory());
        item.put("title", task.getTitle());
        item.put("description", task.getDescription());
        item.put("campus", task.getCampus());
        item.put("location", task.getLocation());
        item.put("deliveryLocation", task.getDeliveryLocation());
        item.put("deadline", task.getDeadline());
        item.put("rewardType", task.getRewardType());
        item.put("rewardDescription", task.getRewardDescription());
        return item;
    }
}
