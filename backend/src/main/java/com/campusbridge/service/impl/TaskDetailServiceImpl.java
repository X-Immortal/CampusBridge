package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.view.ViewDtos;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.CompletionProofMapper;
import com.campusbridge.mapper.ReviewMapper;
import com.campusbridge.mapper.TaskHistoryMapper;
import com.campusbridge.pojo.Attachment;
import com.campusbridge.pojo.CompletionProof;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.TaskDetailService;
import com.campusbridge.service.TaskService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class TaskDetailServiceImpl implements TaskDetailService {
    private static final String OWNER_TYPE_COMPLETION_PROOF = "COMPLETION_PROOF";

    private final TaskService taskService;
    private final CompletionProofMapper completionProofMapper;
    private final AttachmentMapper attachmentMapper;
    private final TaskHistoryMapper taskHistoryMapper;
    private final ReviewMapper reviewMapper;

    public TaskDetailServiceImpl(
            TaskService taskService,
            CompletionProofMapper completionProofMapper,
            AttachmentMapper attachmentMapper,
            TaskHistoryMapper taskHistoryMapper,
            ReviewMapper reviewMapper) {
        this.taskService = taskService;
        this.completionProofMapper = completionProofMapper;
        this.attachmentMapper = attachmentMapper;
        this.taskHistoryMapper = taskHistoryMapper;
        this.reviewMapper = reviewMapper;
    }

    @Override
    public ViewDtos.TaskDetailView getTaskDetail(Long taskId, Long currentUserId) {
        if (taskId == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "taskId must not be null");
        }

        Task task = taskService.getTaskDetail(taskId, currentUserId);

        if (!isParticipant(task, currentUserId)) {
            return new ViewDtos.TaskDetailView(
                    task,
                    Collections.emptyList(),
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList());
        }

        CompletionProof proof = completionProofMapper.selectByTaskId(taskId);
        List<Attachment> attachments = proof == null
                ? Collections.emptyList()
                : attachmentMapper.selectByOwner(OWNER_TYPE_COMPLETION_PROOF, proof.getId());

        return new ViewDtos.TaskDetailView(
                task,
                Collections.emptyList(),
                proof,
                attachments,
                taskHistoryMapper.selectByTaskId(taskId),
                reviewMapper.selectByTaskId(taskId));
    }

    private boolean isParticipant(Task task, Long currentUserId) {
        if (currentUserId == null) {
            return false;
        }
        return currentUserId.equals(task.getRequesterId())
                || currentUserId.equals(task.getProviderId());
    }
}
