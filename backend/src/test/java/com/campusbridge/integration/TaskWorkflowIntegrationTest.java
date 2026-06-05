package com.campusbridge.integration;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.CompletionProof;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.AuthService;
import com.campusbridge.service.CompletionService;
import com.campusbridge.service.ReviewService;
import com.campusbridge.service.TaskApplicationService;
import com.campusbridge.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer integration tests for the core task workflow.
 * Services are mocked so no database schema is required.
 *
 * Normal flow: register → login → publish → apply → accept → start → complete → review
 * Abnormal flow 1: unauthenticated access to protected endpoint
 * Abnormal flow 2: invalid state transition on OPEN task
 * Abnormal flow 3: duplicate application rejected
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TaskWorkflowIntegrationTest {

    private static final Long REQUESTER_ID = 100L;
    private static final Long PROVIDER_ID = 200L;
    private static final Long TASK_ID = 10L;
    private static final Long APPLICATION_ID = 501L;
    private static final Long PROOF_ID = 601L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private TaskApplicationService taskApplicationService;

    @MockitoBean
    private CompletionService completionService;

    @MockitoBean
    private ReviewService reviewService;

    private String requesterToken;
    private String providerToken;

    @BeforeEach
    void setUp() throws Exception {
        // Mock registration
        when(authService.register(any(RequestDtos.RegisterRequest.class)))
                .thenReturn(REQUESTER_ID)
                .thenReturn(PROVIDER_ID);

        // Mock login for requester
        when(authService.login(argThat(req ->
                req != null && "req_test".equals(req.username) && "Test@12345".equals(req.password))))
                .thenReturn(loginPayload(REQUESTER_ID, "req_test", "USER"));

        // Mock login for provider
        when(authService.login(argThat(req ->
                req != null && "prov_test".equals(req.username) && "Test@12345".equals(req.password))))
                .thenReturn(loginPayload(PROVIDER_ID, "prov_test", "USER"));

        // Register + login requester
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf(
                        "username", "req_test",
                        "password", "Test@12345",
                        "phone", "13800000001"
                ))));

        requesterToken = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(mapOf(
                                        "username", "req_test",
                                        "password", "Test@12345"
                                ))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse().getContentAsString())
                .get("data").get("token").asText();

        // Register + login provider
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf(
                        "username", "prov_test",
                        "password", "Test@12345",
                        "phone", "13800000002"
                ))));

        providerToken = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(mapOf(
                                        "username", "prov_test",
                                        "password", "Test@12345"
                                ))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse().getContentAsString())
                .get("data").get("token").asText();
    }

    // ==================== Normal Flow ====================

    @Test
    void fullTaskWorkflowPublishApplyAcceptStartCompleteReview() throws Exception {
        // Mock task creation
        when(taskService.createTask(any(RequestDtos.CreateTaskRequest.class), eq(REQUESTER_ID)))
                .thenReturn(openTask());

        // Mock application
        when(taskApplicationService.apply(eq(TASK_ID), eq(PROVIDER_ID), any()))
                .thenReturn(pendingApplication());

        // Mock list applications
        when(taskApplicationService.listApplications(eq(TASK_ID), eq(REQUESTER_ID)))
                .thenReturn(List.of(pendingApplication()));

        // Mock accept application
        when(taskApplicationService.acceptApplication(eq(TASK_ID), eq(APPLICATION_ID), eq(REQUESTER_ID)))
                .thenReturn(acceptedApplication());

        // Mock status transitions
        when(taskService.transitionTaskStatus(eq(TASK_ID), eq(PROVIDER_ID), any()))
                .thenReturn(taskWithStatus("IN_PROGRESS"));

        // Mock completion proof
        CompletionProof proof = new CompletionProof();
        proof.setId(PROOF_ID);
        proof.setTaskId(TASK_ID);
        proof.setSubmitterId(PROVIDER_ID);
        when(completionService.submitCompletionProof(eq(PROVIDER_ID), eq(TASK_ID), any()))
                .thenReturn(proof);

        // Mock confirm completion
        when(completionService.confirmCompletion(eq(REQUESTER_ID), eq(TASK_ID)))
                .thenReturn(taskWithStatus("COMPLETED"));

        // Step 1: Publish task
        Long taskId = publishTask("帮取快递包裹", "在校门口快递点取一个包裹送到宿舍楼下", 10);

        // Step 2: Provider applies
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/applications")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf(
                                "message", "我经常取快递，可以帮忙"))))
                .andExpect(status().isCreated());

        // Step 3: Requester views applications
        mockMvc.perform(get("/api/v1/tasks/" + taskId + "/applications")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk());

        // Step 4: Requester accepts application
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/applications/" + APPLICATION_ID + "/accept")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk());

        // Step 5: Provider starts task → IN_PROGRESS
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/status-transitions")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("action", "START"))))
                .andExpect(status().isOk());

        // Step 6: Provider submits completion proof
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/completion")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf(
                                "description", "已取到包裹并送到宿舍楼下"))))
                .andExpect(status().isOk());

        // Step 7: Requester confirms completion
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/completion/confirm")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk());

        // Step 8: Reviews
        when(reviewService.createReview(any(RequestDtos.CreateReviewRequest.class)))
                .thenReturn(null);
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/reviews")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf(
                                "revieweeId", PROVIDER_ID,
                                "rating", 5,
                                "comment", "非常靠谱，速度快！"))))
                .andExpect(status().isCreated());
    }

    // ==================== Abnormal Flow 1 ====================

    @Test
    void unauthenticatedAccessToProtectedEndpointsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("title", "test"))))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Abnormal Flow 2 ====================

    @Test
    void invalidStateTransitionOnOpenTaskReturnsConflict() throws Exception {
        when(taskService.createTask(any(RequestDtos.CreateTaskRequest.class), eq(REQUESTER_ID)))
                .thenReturn(openTask());

        Long taskId = publishTask("测试状态流转", "测试非法流转拒绝", 10);

        // START on OPEN task (not yet ACCEPTED) → should fail via service validation
        when(taskService.transitionTaskStatus(eq(taskId), eq(PROVIDER_ID), any()))
                .thenThrow(new com.campusbridge.common.BusinessException(
                        com.campusbridge.common.ErrorCode.TASK_002_INVALID_STATUS_TRANSITION,
                        "非法状态流转"));

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/status-transitions")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("action", "START"))))
                .andExpect(status().isConflict());
    }

    // ==================== Abnormal Flow 3 ====================

    @Test
    void duplicateApplicationIsRejected() throws Exception {
        when(taskService.createTask(any(RequestDtos.CreateTaskRequest.class), eq(REQUESTER_ID)))
                .thenReturn(openTask());

        when(taskApplicationService.apply(eq(TASK_ID), eq(PROVIDER_ID), any()))
                .thenReturn(pendingApplication());

        Long taskId = publishTask("禁止重复接单测试", "测试重复接单被拒", 10);

        // First application succeeds
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/applications")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("message", "申请接单"))))
                .andExpect(status().isCreated());

        // Second application fails — mock service to throw
        when(taskApplicationService.apply(eq(TASK_ID), eq(PROVIDER_ID), any()))
                .thenThrow(new com.campusbridge.common.BusinessException(
                        com.campusbridge.common.ErrorCode.TASK_007_ALREADY_APPLIED,
                        "你已经申请过这个任务"));

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/applications")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("message", "申请接单"))))
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

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        return TASK_ID;
    }

    private static Map<String, Object> loginPayload(Long userId, String username, String role) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("role", role);
        payload.put("token", "jwt-token-" + userId);
        payload.put("refreshToken", "refresh-token-" + userId);
        return payload;
    }

    private Task openTask() {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setRequesterId(REQUESTER_ID);
        task.setStatus("OPEN");
        task.setRewardType("POINTS");
        task.setRewardAmount(20);
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    private Task taskWithStatus(String status) {
        Task task = openTask();
        task.setStatus(status);
        return task;
    }

    private com.campusbridge.pojo.TaskApplication pendingApplication() {
        com.campusbridge.pojo.TaskApplication app = new com.campusbridge.pojo.TaskApplication();
        app.setId(APPLICATION_ID);
        app.setTaskId(TASK_ID);
        app.setApplicantId(PROVIDER_ID);
        app.setStatus("PENDING");
        return app;
    }

    private com.campusbridge.pojo.TaskApplication acceptedApplication() {
        com.campusbridge.pojo.TaskApplication app = pendingApplication();
        app.setStatus("ACCEPTED");
        return app;
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

    /**
     * Custom argThat matcher using a lambda predicate.
     */
    private static <T> T argThat(java.util.function.Predicate<T> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
