package com.campusbridge.dto.request;

import com.campusbridge.enums.TaskStatusTransitionAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusTransitionRequest {
    public TaskStatusTransitionAction action;
    public String reason;
}
