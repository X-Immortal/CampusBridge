package com.campusbridge.controller;

import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.pojo.Post;
import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.Task;
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

    public HomeController(PostMapper postMapper, TaskMapper taskMapper) {
        this.postMapper = postMapper;
        this.taskMapper = taskMapper;
    }

    @GetMapping("/recommendations")
    public Result getRecommendations() {
        List<Post> hotPosts = postMapper.selectHotPosts(8);
        List<Task> latestTasks = taskMapper.selectRecommendedTasks("OPEN", 6);

        Map<String, Object> data = new HashMap<>();
        data.put("hotPosts", hotPosts.stream().map(this::toPublicPost).toList());
        data.put("latestTasks", latestTasks.stream().map(this::toPublicTask).toList());
        return Result.success(data);
    }

    private Map<String, Object> toPublicPost(Post post) {
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

    private Map<String, Object> toPublicTask(Task task) {
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
