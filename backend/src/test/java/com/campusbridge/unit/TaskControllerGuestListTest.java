package com.campusbridge.unit;

import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.controller.TaskController;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.CompletionService;
import com.campusbridge.service.TaskDetailService;
import com.campusbridge.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerGuestListTest {
    private CurrentUserResolver users;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        users = mock(CurrentUserResolver.class);
        when(users.resolveOptionalUserId(any())).thenReturn(null);
        TaskService tasks = mock(TaskService.class);
        Task task = new Task();
        task.setId(1L);
        task.setLocation("Public campus gate");
        task.setProviderId(2L);
        task.setProviderName("Provider");
        task.setRouteHint("Internal route");
        task.setSensitiveReason("Private reason");
        when(tasks.listTasks(null, null, null, null, null, null, false, "CREATED_AT_DESC", 1, 20))
                .thenReturn(Map.of("tasks", List.of(task), "total", 1, "page", 1, "size", 20));
        mvc = MockMvcBuilders.standaloneSetup(new TaskController(
                users, tasks, mock(TaskDetailService.class), mock(CompletionService.class))).build();
    }

    @Test
    void anonymousListMasksTheSameFieldsAsTaskDetail() throws Exception {
        mvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[0].location").value("Public campus gate"))
                .andExpect(jsonPath("$.data.tasks[0].providerId").doesNotExist())
                .andExpect(jsonPath("$.data.tasks[0].providerName").doesNotExist())
                .andExpect(jsonPath("$.data.tasks[0].routeHint").doesNotExist())
                .andExpect(jsonPath("$.data.tasks[0].sensitiveReason").doesNotExist());
    }

    @Test
    void authenticatedListKeepsExistingTaskFields() throws Exception {
        when(users.resolveOptionalUserId(any())).thenReturn(1L);
        mvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[0].providerId").value(2L))
                .andExpect(jsonPath("$.data.tasks[0].providerName").value("Provider"))
                .andExpect(jsonPath("$.data.tasks[0].routeHint").value("Internal route"));
    }
}
