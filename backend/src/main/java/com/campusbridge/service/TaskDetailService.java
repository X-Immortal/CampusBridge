package com.campusbridge.service;

import com.campusbridge.dto.view.TaskDetailView;

public interface TaskDetailService {
    TaskDetailView getTaskDetail(Long taskId, Long currentUserId);
}
