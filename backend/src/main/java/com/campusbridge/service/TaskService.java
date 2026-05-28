package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Task;

import java.util.List;

public interface TaskService {
    Task createTask(Long requesterId, RequestDtos.CreateTaskRequest request);

    Task transitionTaskStatus(
            Long operatorId,
            Long taskId,
            RequestDtos.TaskStatusTransitionRequest request);

    List<Task> listTasks(
            String keyword,
            String category,
            String campus,
            String status,
            String rewardType,
            Boolean requiresProof,
            String sortBy,
            Integer page,
            Integer size);

    Task getTaskDetail(Long taskId);

    Task updateTask(Long operatorId, Long taskId, RequestDtos.UpdateTaskRequest request);

    Task cancelTask(Long operatorId, Long taskId, String reason);

    Task saveDraft(Long requesterId, RequestDtos.CreateTaskRequest request);

    Task resendTask(Long operatorId, Long taskId);
}