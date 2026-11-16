package com.campusbridge.unit;

import com.campusbridge.dto.query.TaskQuery;
import com.campusbridge.dto.view.TaskPageView;
import org.mockito.ArgumentCaptor;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.controller.TaskController;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.service.CompletionService;
import com.campusbridge.service.TaskDetailService;
import com.campusbridge.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerGuestListTest {
    private CurrentUserResolver users;
    private MockMvc mvc;
    private TaskService tasks;

    @BeforeEach
    void setUp() {
        users = mock(CurrentUserResolver.class);
        when(users.resolveOptionalUserId(any())).thenReturn(null);
        tasks = mock(TaskService.class);
        TaskView task = new TaskView();
        task.setId(1L);
        task.setLocation("Public campus gate");
        task.setProviderId(2L);
        task.setProviderName("Provider");
        task.setRouteHint("Internal route");
        task.setSensitiveReason("Private reason");
        when(tasks.listTasks(any(TaskQuery.class)))
                .thenReturn(new TaskPageView(List.of(task), 1, 1, 20));
        mvc = MockMvcBuilders.standaloneSetup(new TaskController(
                users, tasks, mock(TaskDetailService.class), mock(CompletionService.class))).build();
    }

    @Test
    void anonymousListMasksTheSameFieldsAsTaskDetail() throws Exception {
        mvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
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
    @Test
    void publicQueryBindsFiltersWithoutAcceptingPrivateVisibility() throws Exception {
        mvc.perform(get("/api/v1/tasks")
                        .param("keyword", "parcel")
                        .param("category", "ERRAND")
                        .param("campus", "Test Campus")
                        .param("status", "OPEN")
                        .param("rewardType", "POINTS")
                        .param("requiresProof", "true")
                        .param("excludeExpired", "true")
                        .param("sortBy", "DEADLINE_ASC")
                        .param("page", "2")
                        .param("size", "5")
                        .param("includePrivate", "true"))
                .andExpect(status().isOk());

        ArgumentCaptor<TaskQuery> query = ArgumentCaptor.forClass(TaskQuery.class);
        verify(tasks).listTasks(query.capture());
        assertThat(query.getValue()).isEqualTo(TaskQuery.builder()
                .keyword("parcel").category("ERRAND").campus("Test Campus")
                .status("OPEN").rewardType("POINTS").requiresProof(true)
                .excludeExpired(true).sortBy("DEADLINE_ASC").page(2).size(5).build());
    }

}
