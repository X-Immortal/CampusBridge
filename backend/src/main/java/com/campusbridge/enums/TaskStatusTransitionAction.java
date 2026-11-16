package com.campusbridge.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum TaskStatusTransitionAction {
    START(TaskStatus.IN_PROGRESS, TaskHistoryEventType.STARTED, false,
            Set.of(TaskStatus.ACCEPTED)),
    CONFIRM_COMPLETION(TaskStatus.COMPLETED, TaskHistoryEventType.COMPLETION_CONFIRMED, false,
            Set.of(TaskStatus.PENDING_CONFIRMATION)),
    REJECT_COMPLETION(TaskStatus.IN_PROGRESS, TaskHistoryEventType.COMPLETION_REJECTED, true,
            Set.of(TaskStatus.PENDING_CONFIRMATION)),
    DISPUTE(TaskStatus.DISPUTED, TaskHistoryEventType.DISPUTED, true,
            Set.of(TaskStatus.ACCEPTED, TaskStatus.IN_PROGRESS, TaskStatus.PENDING_CONFIRMATION)),
    CANCEL(TaskStatus.CANCELLED, TaskHistoryEventType.CANCELLED, true,
            Set.of(TaskStatus.OPEN, TaskStatus.ACCEPTED, TaskStatus.IN_PROGRESS));

    private final TaskStatus targetStatus;
    private final TaskHistoryEventType historyEventType;
    private final boolean reasonRequired;
    private final Set<TaskStatus> allowedFrom;

    public boolean canTransitionFrom(TaskStatus status) {
        return allowedFrom.contains(status);
    }
}
