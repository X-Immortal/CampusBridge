package com.campusbridge.unit;

import com.campusbridge.dto.view.TaskView;
import com.campusbridge.entity.Attachment;
import com.campusbridge.entity.CompletionProof;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.CompletionProofMapper;
import com.campusbridge.mapper.ReviewMapper;
import com.campusbridge.mapper.TaskHistoryMapper;
import com.campusbridge.service.TaskService;
import com.campusbridge.service.impl.TaskDetailServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskDetailServiceImplTest {
    private static final Long TASK_ID = 10L;
    private static final Long REQUESTER_ID = 1L;
    private static final Long PROVIDER_ID = 2L;

    @Mock
    private TaskService taskService;

    @Mock
    private CompletionProofMapper completionProofMapper;

    @Mock
    private AttachmentMapper attachmentMapper;

    @Mock
    private TaskHistoryMapper taskHistoryMapper;

    @Mock
    private ReviewMapper reviewMapper;

    private TaskDetailServiceImpl taskDetailService;

    @BeforeEach
    void setUp() {
        taskDetailService = new TaskDetailServiceImpl(
                taskService,
                completionProofMapper,
                attachmentMapper,
                taskHistoryMapper,
                reviewMapper);
    }

    @Test
    void requesterCanSeeCompletionProofAndAttachments() {
        TaskView task = task();
        CompletionProof proof = proof();
        Attachment attachment = new Attachment();
        attachment.setId(30L);
        when(taskService.getTaskDetail(TASK_ID, REQUESTER_ID)).thenReturn(task);
        when(completionProofMapper.selectByTaskId(TASK_ID)).thenReturn(proof);
        when(attachmentMapper.selectByOwner("COMPLETION_PROOF", proof.getId()))
                .thenReturn(List.of(attachment));
        when(taskHistoryMapper.selectByTaskId(TASK_ID)).thenReturn(List.of());
        when(reviewMapper.selectByTaskId(TASK_ID)).thenReturn(List.of());

        var detail = taskDetailService.getTaskDetail(TASK_ID, REQUESTER_ID);

        assertThat(detail.getCompletionProof()).isSameAs(proof);
        assertThat(detail.getAttachments()).containsExactly(attachment);
    }

    @Test
    void providerCanSeeOwnCompletionProofAndAttachments() {
        TaskView task = task();
        CompletionProof proof = proof();
        when(taskService.getTaskDetail(TASK_ID, PROVIDER_ID)).thenReturn(task);
        when(completionProofMapper.selectByTaskId(TASK_ID)).thenReturn(proof);
        when(attachmentMapper.selectByOwner("COMPLETION_PROOF", proof.getId()))
                .thenReturn(List.of());
        when(taskHistoryMapper.selectByTaskId(TASK_ID)).thenReturn(List.of());
        when(reviewMapper.selectByTaskId(TASK_ID)).thenReturn(List.of());

        var detail = taskDetailService.getTaskDetail(TASK_ID, PROVIDER_ID);

        assertThat(detail.getCompletionProof()).isSameAs(proof);
    }

    @Test
    void unrelatedUserCannotSeeCompletionProof() {
        when(taskService.getTaskDetail(TASK_ID, 99L)).thenReturn(task());

        var detail = taskDetailService.getTaskDetail(TASK_ID, 99L);

        assertThat(detail.getCompletionProof()).isNull();
        assertThat(detail.getAttachments()).isEmpty();
        verify(completionProofMapper, never()).selectByTaskId(TASK_ID);
    }

    private TaskView task() {
        TaskView task = new TaskView();
        task.setId(TASK_ID);
        task.setRequesterId(REQUESTER_ID);
        task.setProviderId(PROVIDER_ID);
        task.setStatus("PENDING_CONFIRMATION");
        return task;
    }

    private CompletionProof proof() {
        CompletionProof proof = new CompletionProof();
        proof.setId(20L);
        proof.setTaskId(TASK_ID);
        proof.setSubmitterId(PROVIDER_ID);
        proof.setDescription("已送达指定地点");
        return proof;
    }
}
