package com.campusbridge.service;

import com.campusbridge.dto.view.ViewDtos;

public interface TaskDetailService {
    ViewDtos.TaskDetailView getTaskDetail(Long taskId, Long currentUserId);
}
