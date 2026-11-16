package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.view.TaskDetailView;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.entity.Attachment;
import com.campusbridge.entity.CompletionProof;
import com.campusbridge.enums.AttachmentOwnerType;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.CompletionProofMapper;
import com.campusbridge.mapper.ReviewMapper;
import com.campusbridge.mapper.TaskHistoryMapper;
import com.campusbridge.service.TaskDetailService;
import com.campusbridge.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskDetailServiceImpl implements TaskDetailService {

    private final TaskService taskService;
    private final CompletionProofMapper completionProofMapper;
    private final AttachmentMapper attachmentMapper;
    private final TaskHistoryMapper taskHistoryMapper;
    private final ReviewMapper reviewMapper;

    @Override
    public TaskDetailView getTaskDetail(Long taskId, Long currentUserId) {
        if (taskId == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "taskId must not be null");
        }

        TaskView task = taskService.getTaskDetail(taskId, currentUserId);

        if (!isParticipant(task, currentUserId)) {
            return new TaskDetailView(
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
                : attachmentMapper.selectByOwner(AttachmentOwnerType.COMPLETION_PROOF, proof.getId());

        return new TaskDetailView(
                task,
                Collections.emptyList(),
                proof,
                attachments,
                taskHistoryMapper.selectByTaskId(taskId),
                reviewMapper.selectByTaskId(taskId));
    }

    private boolean isParticipant(TaskView task, Long currentUserId) {
        if (currentUserId == null) {
            return false;
        }
        return currentUserId.equals(task.getRequesterId())
                || currentUserId.equals(task.getProviderId());
    }
}
