package com.campusbridge.integration;

import com.campusbridge.dto.query.NotificationQuery;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.view.NotificationView;
import com.campusbridge.entity.User;
import com.campusbridge.enums.NotificationType;
import com.campusbridge.enums.ContentItemType;
import com.campusbridge.enums.UserRole;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.NotificationService;
import com.campusbridge.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @MockitoBean NotificationService notifications;
    @MockitoBean UserMapper users;

    @Test
    void notificationRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/notifications/1/read")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
        verifyNoInteractions(notifications);
    }

    @Test
    void listBindsTypedFiltersAndReturnsIndependentRecords() throws Exception {
        String token = token();
        var first = new NotificationView(11L, NotificationType.TASK_APPLICATION_CREATED, "申请", "正文",
                ContentItemType.TASK, 9L, null, LocalDateTime.now());
        var second = new NotificationView(12L, NotificationType.TASK_COMPLETED, "完成", "正文",
                ContentItemType.TASK, 9L, null, LocalDateTime.now());
        when(notifications.list(eq(1L), any(NotificationQuery.class)))
                .thenReturn(new PagedResult<>(List.of(first, second), 1, 20, 2L));
        mvc.perform(get("/api/v1/notifications").header("Authorization", token)
                        .param("types", "TASK_APPLICATION_CREATED,TASK_COMPLETED").param("unreadOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].type").value("TASK_APPLICATION_CREATED"))
                .andExpect(jsonPath("$.data.items[0].relatedId").value(9))
                .andExpect(jsonPath("$.data.items[0].conversationId").doesNotExist());
        verify(notifications).list(eq(1L), argThat(q -> q.isUnreadOnly() && q.getTypes().size() == 2));
    }

    @Test
    void invalidFilterAndPaginationAreRejectedBeforeQuerying() throws Exception {
        String token = token();
        mvc.perform(get("/api/v1/notifications").header("Authorization", token).param("types", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/notifications").header("Authorization", token).param("size", "101"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(notifications);
    }

    private String token() {
        User user = new User();
        user.setId(1L);
        user.setUsername("notification-test");
        user.setRole(UserRole.USER);
        user.setEnabled(1);
        when(users.selectById(1L)).thenReturn(user);
        return "Bearer " + jwt.generateToken(1L, UserRole.USER);
    }
}
