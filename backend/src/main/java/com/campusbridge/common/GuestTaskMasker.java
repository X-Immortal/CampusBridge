package com.campusbridge.common;

import com.campusbridge.dto.view.TaskView;

/**
 * Utility for masking sensitive task fields when serving anonymous (guest) requests.
 * Guest users should not see provider identity, internal routing hints, or sensitive
 * reasons that may include contact information.
 */
public final class GuestTaskMasker {

    private GuestTaskMasker() {
    }

    public static boolean isAnonymous(Long currentUserId) {
        return currentUserId == null;
    }

    public static TaskView maskForGuest(TaskView task, Long currentUserId) {
        if (task == null || !isAnonymous(currentUserId)) {
            return task;
        }
        task.setProviderId(null);
        task.setProviderName(null);
        task.setRouteHint(null);
        task.setSensitiveReason(null);
        return task;
    }
}
