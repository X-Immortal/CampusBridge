package com.campusbridge.service;

import com.campusbridge.dto.query.TaskQuery;
import com.campusbridge.dto.view.TaskPageView;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.view.TaskView;


public interface TaskService {
    TaskView createTask(Long requesterId, RequestDtos.CreateTaskRequest request);

    TaskView transitionTaskStatus(
            Long operatorId,
            Long taskId,
            RequestDtos.TaskStatusTransitionRequest request);

    TaskPageView listTasks(TaskQuery query);

    default TaskView getTaskDetail(Long taskId) {
        return getTaskDetail(taskId, null);
    }

    TaskView getTaskDetail(Long taskId, Long currentUserId);

    TaskView updateTask(Long operatorId, Long taskId, RequestDtos.UpdateTaskRequest request);

    TaskView cancelTask(Long operatorId, Long taskId, String reason);

    TaskView withdrawFromTask(Long providerId, Long taskId, String reason);

    void deleteDraft(Long operatorId, Long taskId);

    TaskView saveDraft(Long requesterId, RequestDtos.CreateTaskRequest request);

    TaskView resendTask(Long operatorId, Long taskId);
}
