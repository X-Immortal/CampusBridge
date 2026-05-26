package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.mapper.TaskHistoryMapper;
import com.campusbridge.pojo.TaskHistory;
import com.campusbridge.service.impl.TaskHistoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BE11TaskHistoryServiceImplTest {
    @Mock
    private TaskHistoryMapper taskHistoryMapper;

    private TaskHistoryServiceImpl taskHistoryService;

    @BeforeEach
    void setUp() {
        taskHistoryService = new TaskHistoryServiceImpl(taskHistoryMapper);
    }

    @Test
    void recordHistoryPersistsAuditEntry() {
        when(taskHistoryMapper.insert(any(TaskHistory.class))).thenReturn(1);

        taskHistoryService.recordHistory(
                10L,
                "OPEN",
                "ACCEPTED",
                1L,
                "APPLICATION_ACCEPTED");

        ArgumentCaptor<TaskHistory> captor = ArgumentCaptor.forClass(TaskHistory.class);
        verify(taskHistoryMapper).insert(captor.capture());
        TaskHistory saved = captor.getValue();
        assertThat(saved.getTaskId()).isEqualTo(10L);
        assertThat(saved.getFromStatus()).isEqualTo("OPEN");
        assertThat(saved.getToStatus()).isEqualTo("ACCEPTED");
        assertThat(saved.getOperatorId()).isEqualTo(1L);
        assertThat(saved.getNote()).isEqualTo("APPLICATION_ACCEPTED");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void recordHistoryRejectsIncompleteData() {
        assertThatThrownBy(() -> taskHistoryService.recordHistory(10L, "OPEN", " ", 1L, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.COMMON_001_VALIDATION_ERROR));
    }
}
