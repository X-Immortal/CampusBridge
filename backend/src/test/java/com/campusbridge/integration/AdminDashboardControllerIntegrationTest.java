package com.campusbridge.integration;

import com.campusbridge.enums.DashboardTodoType;
import com.campusbridge.dto.view.DashboardDistributionItem;
import com.campusbridge.dto.view.DashboardSummary;
import com.campusbridge.dto.view.DashboardTodo;
import com.campusbridge.dto.view.DashboardTrend;
import com.campusbridge.entity.User;
import com.campusbridge.enums.UserRole;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.AdminDashboardService;
import com.campusbridge.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminDashboardControllerIntegrationTest {

    private static final Long ADMIN_ID = 99L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private AdminDashboardService adminDashboardService;

    private String adminAuthHeader;

    @BeforeEach
    void setUp() {
        User admin = new User();
        admin.setId(ADMIN_ID);
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(1);
        when(userMapper.selectById(ADMIN_ID)).thenReturn(admin);
        adminAuthHeader = "Bearer " + jwtUtil.generateToken(ADMIN_ID, UserRole.ADMIN);
    }

    @Test
    void summaryReturnsAggregatedPayload() throws Exception {
        DashboardSummary summary = new DashboardSummary();
        summary.setUserTotal(120L);
        summary.setVerifiedUserTotal(95L);
        summary.setTaskTotal(340L);
        summary.setActiveTaskTotal(68L);
        summary.setCompletedTaskTotal(210L);
        summary.setPostTotal(180L);
        summary.setPendingVerificationTotal(6L);
        summary.setPendingReportTotal(4L);
        summary.setDisputedTaskTotal(2L);
        summary.setUpdatedAt(LocalDateTime.of(2026, 7, 1, 0, 30));
        when(adminDashboardService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.userTotal").value(120))
                .andExpect(jsonPath("$.data.verifiedUserTotal").value(95))
                .andExpect(jsonPath("$.data.taskTotal").value(340))
                .andExpect(jsonPath("$.data.pendingVerificationTotal").value(6))
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    void trendsRespectsDaysParameterAndClampsRange() throws Exception {
        DashboardTrend trend = new DashboardTrend(
                List.of("06-25", "06-26", "06-27"),
                List.of(3L, 5L, 2L),
                List.of(9L, 12L, 8L),
                List.of(5L, 8L, 6L));
        when(adminDashboardService.getTrends(3)).thenReturn(trend);

        mockMvc.perform(get("/api/v1/admin/dashboard/trends")
                        .param("days", "3")
                        .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.days[0]").value("06-25"))
                .andExpect(jsonPath("$.data.newUsers[2]").value(2))
                .andExpect(jsonPath("$.data.newTasks[0]").value(9));
    }

    @Test
    void trendsDefaultsToSevenDaysWhenParameterMissing() throws Exception {
        DashboardTrend trend = new DashboardTrend(
                List.of("06-25", "06-26", "06-27", "06-28", "06-29", "06-30", "07-01"),
                List.of(3L, 5L, 2L, 4L, 7L, 6L, 4L),
                List.of(9L, 12L, 8L, 15L, 18L, 13L, 11L),
                List.of(5L, 8L, 6L, 7L, 11L, 9L, 10L));
        when(adminDashboardService.getTrends(7)).thenReturn(trend);

        mockMvc.perform(get("/api/v1/admin/dashboard/trends")
                        .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(7));
    }

    @Test
    void taskStatusStatsReturnsDistributionList() throws Exception {
        List<DashboardDistributionItem> stats = List.of(
                new DashboardDistributionItem("OPEN", 48L),
                new DashboardDistributionItem("COMPLETED", 210L));
        when(adminDashboardService.getTaskStatusStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/admin/dashboard/task-status")
                        .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].code").value("OPEN"))
                .andExpect(jsonPath("$.data[0].label").doesNotExist())
                .andExpect(jsonPath("$.data[1].count").value(210));
    }

    @Test
    void reportStatusAndTargetTypeEndpointsReturnLists() throws Exception {
        when(adminDashboardService.getReportStatusStats()).thenReturn(List.of(
                new DashboardDistributionItem("PENDING", 4L)));
        when(adminDashboardService.getReportTargetTypeStats()).thenReturn(List.of(
                new DashboardDistributionItem("TASK", 12L)));

        mockMvc.perform(get("/api/v1/admin/dashboard/report-status")
                        .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("PENDING"));

        mockMvc.perform(get("/api/v1/admin/dashboard/report-target-type")
                        .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("TASK"))
                .andExpect(jsonPath("$.data[0].label").doesNotExist());
    }

    @Test
    void todosReturnsPendingAdminSummary() throws Exception {
        List<DashboardTodo> todos = List.of(
                new DashboardTodo(DashboardTodoType.VERIFICATION, "待认证申请", 6L, "/admin?section=verifications"),
                new DashboardTodo(DashboardTodoType.REPORT, "待处理举报", 4L, "/admin?section=reports"),
                new DashboardTodo(DashboardTodoType.DISPUTED_TASK, "争议任务", 2L, "/admin?section=dashboard"));
        when(adminDashboardService.getTodos()).thenReturn(todos);

        mockMvc.perform(get("/api/v1/admin/dashboard/todos")
                        .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].type").value("VERIFICATION"))
                .andExpect(jsonPath("$.data[0].count").value(6))
                .andExpect(jsonPath("$.data[1].route").value("/admin?section=reports"));
    }

    @Test
    void adminEndpointRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001_UNAUTHORIZED"));
    }

    @Test
    void adminEndpointRejectsNonAdminRole() throws Exception {
        User user = new User();
        user.setId(ADMIN_ID);
        user.setRole(UserRole.USER);
        user.setEnabled(1);
        when(userMapper.selectById(ADMIN_ID)).thenReturn(user);
        String userToken = "Bearer " + jwtUtil.generateToken(ADMIN_ID, UserRole.USER);

        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header("Authorization", userToken))
                .andExpect(status().isForbidden());
    }
}
