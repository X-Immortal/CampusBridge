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
        data.put("hotPosts", hotPosts);
        data.put("latestTasks", latestTasks);
        return Result.success(data);
    }
}
