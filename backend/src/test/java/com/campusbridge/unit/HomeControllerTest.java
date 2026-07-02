package com.campusbridge.unit;

import com.campusbridge.controller.HomeController;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.pojo.Post;
import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.RecommendationService;
import com.campusbridge.utils.AuthContextUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeControllerTest {
    @Mock
    private PostMapper postMapper;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private RecommendationService recommendationService;

    @Mock
    private AuthContextUtil authContextUtil;

    @Test
    @SuppressWarnings("unchecked")
    void recommendationsExposeOnlyPublicTaskAndPostFields() {
        Post post = new Post();
        post.setId(1L);
        post.setAuthorId(2L);
        post.setAuthorName("同学甲");
        post.setTitle("热门帖子");

        Task task = new Task();
        task.setId(3L);
        task.setRequesterId(4L);
        task.setTitle("代取快递");
        task.setDeliveryLocation("5号宿舍楼");
        task.setSensitiveReason("不应公开的审核信息");
        task.setRouteHint("不应公开的内部路线信息");

        when(postMapper.selectHotPosts(8)).thenReturn(List.of(post));
        when(taskMapper.selectRecommendedTasks("OPEN", 6)).thenReturn(List.of(task));
        when(authContextUtil.getCurrentUserId()).thenReturn(null);

        Result result = new HomeController(postMapper, taskMapper,
                recommendationService, authContextUtil).getRecommendations();
        Map<String, Object> data = (Map<String, Object>) result.getData();
        Map<String, Object> publicPost = ((List<Map<String, Object>>) data.get("hotPosts")).get(0);
        Map<String, Object> publicTask = ((List<Map<String, Object>>) data.get("latestTasks")).get(0);

        assertThat(publicPost).containsEntry("authorName", "同学甲");
        assertThat(publicTask).containsEntry("deliveryLocation", "5号宿舍楼");
        assertThat(publicTask).doesNotContainKeys("sensitiveReason", "routeHint", "providerId");
    }
}
