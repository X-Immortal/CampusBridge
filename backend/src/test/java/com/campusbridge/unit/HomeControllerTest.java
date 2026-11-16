package com.campusbridge.unit;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.controller.HomeController;
import com.campusbridge.dto.view.HomeRecommendationsView;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.service.RecommendationService;
import com.campusbridge.utils.AuthContextUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
    void recommendationsExposeOnlyPublicTaskAndPostFields() {
        PostView post = new PostView();
        post.setId(1L);
        post.setAuthorId(2L);
        post.setAuthorName("同学甲");
        post.setTitle("热门帖子");

        TaskView task = new TaskView();
        task.setId(3L);
        task.setRequesterId(4L);
        task.setTitle("代取快递");
        task.setDeliveryLocation("5号宿舍楼");
        task.setSensitiveReason("不应公开的审核信息");
        task.setRouteHint("不应公开的内部路线信息");

        when(postMapper.selectHotPosts(8)).thenReturn(List.of(post));
        when(taskMapper.selectRecommendedTasks(TaskStatus.OPEN, 6)).thenReturn(List.of(task));
        when(authContextUtil.getCurrentUserId()).thenReturn(null);

        ApiResponse<HomeRecommendationsView> result = new HomeController(postMapper, taskMapper,
                recommendationService, authContextUtil).getRecommendations();
        HomeRecommendationsView data = result.getData();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().valueToTree(data);
        assertThat(json.at("/hotPosts/0/authorName").asText()).isEqualTo("同学甲");
        assertThat(json.at("/latestTasks/0/deliveryLocation").asText()).isEqualTo("5号宿舍楼");
        assertThat(json.at("/latestTasks/0").has("sensitiveReason")).isFalse();
        assertThat(json.at("/latestTasks/0").has("routeHint")).isFalse();
        assertThat(json.at("/latestTasks/0").has("providerId")).isFalse();
    }
}
