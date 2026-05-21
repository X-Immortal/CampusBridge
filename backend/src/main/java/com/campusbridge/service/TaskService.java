package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Task;

public interface TaskService {
    Task createTask(Long requesterId, RequestDtos.CreateTaskRequest request);

    Task transitionTaskStatus(
            Long operatorId,
            Long taskId,
            RequestDtos.TaskStatusTransitionRequest request);
}
