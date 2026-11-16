package com.campusbridge.integration;

import com.campusbridge.dto.query.TaskQuery;
import com.campusbridge.dto.view.TaskPageView;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.pojo.User;

import com.campusbridge.dto.view.TaskView;
import com.campusbridge.dto.view.ViewDtos;
import com.campusbridge.service.PostService;
import com.campusbridge.service.TaskDetailService;
import com.campusbridge.service.TaskService;
import com.campusbridge.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests covering guest (anonymous) access.
 * Verifies that:
 *  - public read endpoints accept requests without a JWT
 *  - sensitive fields are masked when the caller is anonymous
 *  - write endpoints and protected reads still require authentication
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GuestAccessIntegrationTest {

    private static final Long REQUESTER_ID = 100L;
    private static final Long TASK_ID = 10L;
    private static final Long POST_ID = 20L;
    private static final Long USER_ID = 30L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private TaskDetailService taskDetailService;

    @MockitoBean
    private PostService postService;

    private String userToken() {
        User user = new User();
        user.setId(USER_ID);
        user.setRole("USER");
        user.setEnabled(1);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        return "Bearer " + jwtUtil.generateToken(USER_ID, "USER");
    }

    private TaskView publicTask() {
        TaskView task = new TaskView();
        task.setId(TASK_ID);
        task.setRequesterId(REQUESTER_ID);
        task.setProviderId(999L);
        task.setProviderName("已选帮助者");
        task.setRouteHint("请走西门");
        task.setSensitiveReason("仅站内沟通");
        task.setStatus("OPEN");
        task.setTitle("帮取快递");
        task.setDescription("从快递点取一个包裹");
        task.setCampus("仙林校区");
        task.setLocation("南门");
        task.setRewardType("POINTS");
        task.setRewardAmount(10);
        task.setOnlyInAppContact(1);
        task.setAllowOfflineContact(0);
        task.setRequiresProof(1);
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    private ViewDtos.TaskDetailView publicTaskDetail() {
        return new ViewDtos.TaskDetailView(
                publicTask(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    void guestCanListTasksWithoutToken() throws Exception {
        when(taskService.listTasks(any(TaskQuery.class)))
                .thenReturn(new TaskPageView(List.of(), 0, 1, 20));

        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    void guestCanReadTaskDetailButSensitiveFieldsAreMasked() throws Exception {
        when(taskDetailService.getTaskDetail(eq(TASK_ID), isNull())).thenReturn(publicTaskDetail());

        mockMvc.perform(get("/api/v1/tasks/" + TASK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.task.id").value(TASK_ID))
                .andExpect(jsonPath("$.data.task.title").value("帮取快递"))
                .andExpect(jsonPath("$.data.task.providerName").doesNotExist())
                .andExpect(jsonPath("$.data.task.providerId").doesNotExist())
                .andExpect(jsonPath("$.data.task.routeHint").doesNotExist())
                .andExpect(jsonPath("$.data.task.sensitiveReason").doesNotExist());
    }

    @Test
    void authenticatedUserSeesSensitiveFields() throws Exception {
        when(taskDetailService.getTaskDetail(eq(TASK_ID), eq(USER_ID))).thenReturn(publicTaskDetail());

        mockMvc.perform(get("/api/v1/tasks/" + TASK_ID)
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.task.id").value(TASK_ID))
                .andExpect(jsonPath("$.data.task.providerName").value("已选帮助者"));
    }

    @Test
    void guestCanReadPostDetail() throws Exception {
        when(postService.getPostDetail(eq(POST_ID), isNull())).thenReturn(null);

        mockMvc.perform(get("/api/v1/posts/" + POST_ID))
                .andExpect(status().isOk());
    }

    @Test
    void guestCannotCreateTask() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType("application/json")
                        .content("{\"title\":\"test\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001_UNAUTHORIZED"));
    }

    @Test
    void guestCannotAccessMyAccount() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001_UNAUTHORIZED"));
    }
}
