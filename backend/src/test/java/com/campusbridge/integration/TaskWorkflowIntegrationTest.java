package com.campusbridge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the core task workflow.
 *
 * Normal flow: register two users → login → publish task → apply → accept → start → complete → review
 * Abnormal flow 1: unauthenticated access to protected endpoint
 * Abnormal flow 2: invalid state transition on OPEN task
 * Abnormal flow 3: duplicate application rejected
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TaskWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String requesterToken;
    private String providerToken;
    private Long requesterId;
    private Long providerId;

    @BeforeEach
    void setUp() throws Exception {
        String requesterUsername = "req_" + System.nanoTime();
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf(
                        "username", requesterUsername,
                        "password", "Test@12345",
                        "phone", "1380000" + (1000 + (int) (Math.random() * 9000)),
                        "email", requesterUsername + "@test.edu.cn"
                ))));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf(
                                "username", requesterUsername,
                                "password", "Test@12345"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> loginData = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) loginData.get("data");
        requesterToken = (String) data.get("token");
        requesterId = ((Number) data.get("userId")).longValue();

        // Insert verification record for requester (required for task publishing)
        jdbcTemplate.update(
                "INSERT INTO campus_verifications (user_id, method, real_name_cipher, student_number_cipher, school, status) "
                        + "VALUES (?, 'MANUAL_REVIEW', 'test', 'test', '测试大学', 'VERIFIED')",
                requesterId);

        String providerUsername = "prov_" + System.nanoTime();
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf(
                        "username", providerUsername,
                        "password", "Test@12345",
                        "phone", "1380000" + (5000 + (int) (Math.random() * 4000)),
                        "email", providerUsername + "@test.edu.cn"
                ))));

        MvcResult providerLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf(
                                "username", providerUsername,
                                "password", "Test@12345"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> pLoginData = objectMapper.readValue(
                providerLogin.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> pData = (Map<String, Object>) pLoginData.get("data");
        providerToken = (String) pData.get("token");
        providerId = ((Number) pData.get("userId")).longValue();

        // Insert verification record for provider
        jdbcTemplate.update(
                "INSERT INTO campus_verifications (user_id, method, real_name_cipher, student_number_cipher, school, status) "
                        + "VALUES (?, 'MANUAL_REVIEW', 'test', 'test', '测试大学', 'VERIFIED')",
                providerId);
    }

    // ==================== Normal Flow ====================

    @Test
    @SuppressWarnings("unchecked")
    void fullTaskWorkflowPublishApplyAcceptStartCompleteReview() throws Exception {
        // Step 1: Publish a task
        Long taskId = publishTask("帮取快递包裹", "在校门口快递点取一个包裹送到宿舍楼下", 30);

        // Step 2: Provider applies for the task
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/applications")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf(
                                "message", "我经常取快递，可以帮忙"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // Step 3: Requester views applications and gets the application ID
        MvcResult listResult = mockMvc.perform(get("/api/v1/tasks/" + taskId + "/applications")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();

        Map<String, Object> listResponse = objectMapper.readValue(
                listResult.getResponse().getContentAsString(), Map.class);
        List<Map<String, Object>> applications = (List<Map<String, Object>>) listResponse.get("data");
        Long applicationId = ((Number) applications.get(0).get("applicationId")).longValue();
        assertThat(applicationId).isPositive();

        // Step 4: Requester accepts the application
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/applications/" + applicationId + "/accept")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk());

        // Step 5: Provider starts the task → IN_PROGRESS
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/status-transitions")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("action", "START"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        // Step 6: Provider submits completion proof
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/completion")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf(
                                "description", "已取到包裹并送到宿舍楼下"))))
                .andExpect(status().isOk());

        // Step 7: Requester confirms completion → COMPLETED
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/completion/confirm")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk());

        // Step 8: Both parties submit reviews
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/reviews")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf(
                                "revieweeId", providerId,
                                "rating", 5,
                                "comment", "非常靠谱，速度快！"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/reviews")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf(
                                "revieweeId", requesterId,
                                "rating", 4,
                                "comment", "任务描述清楚，合作愉快"))))
                .andExpect(status().isCreated());
    }

    // ==================== Abnormal Flow 1: Unauthenticated Access ====================

    @Test
    void unauthenticatedAccessToProtectedEndpointsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("title", "test"))))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Abnormal Flow 2: Invalid State Transition ====================

    @Test
    void invalidStateTransitionOnOpenTaskReturnsConflict() throws Exception {
        Long taskId = publishTask("测试状态流转", "测试非法流转拒绝", 10);

        // START on OPEN task (not yet ACCEPTED) → should fail
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/status-transitions")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("action", "START"))))
                .andExpect(status().isConflict());

        // CONFIRM_COMPLETION on OPEN task → should fail
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/status-transitions")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("action", "CONFIRM_COMPLETION"))))
                .andExpect(status().isConflict());
    }

    // ==================== Abnormal Flow 3: Duplicate Application ====================

    @Test
    void duplicateApplicationIsRejected() throws Exception {
        Long taskId = publishTask("禁止重复接单测试", "测试重复接单被拒", 10);

        String appBody = objectMapper.writeValueAsString(mapOf("message", "申请接单"));

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/applications")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/applications")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_007_ALREADY_APPLIED"));
    }

    // ==================== Helpers ====================

    private Long publishTask(String title, String description, int rewardAmount) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("category", "ERRAND");
        body.put("title", title);
        body.put("description", description);
        body.put("campus", "仙林校区");
        body.put("location", "图书馆");
        body.put("deadline", LocalDateTime.now().plusDays(1).toString());
        body.put("rewardType", "POINTS");
        body.put("rewardAmount", rewardAmount);
        body.put("completionStandard", "按要求完成并拍照确认");
        body.put("requiresProof", false);
        body.put("onlyInAppContact", true);

        MvcResult result = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> taskData = (Map<String, Object>) response.get("data");
        Long taskId = ((Number) taskData.get("id")).longValue();
        assertThat(taskId).isPositive();
        return taskId;
    }

    @SafeVarargs
    private static <K, V> Map<K, V> mapOf(Object... entries) {
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            @SuppressWarnings("unchecked")
            K key = (K) entries[i];
            @SuppressWarnings("unchecked")
            V value = (V) entries[i + 1];
            map.put(key, value);
        }
        return map;
    }
}