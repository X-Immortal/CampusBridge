package com.campusbridge.dto.request;

import com.campusbridge.enums.TaskStatusTransitionAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusTransitionRequest {
    @NotNull(message = "action is required")
    public TaskStatusTransitionAction action;

    @Size(max = 300, message = "reason is too long")
    public String reason;
}
