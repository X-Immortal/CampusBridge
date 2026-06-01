package com.campusbridge.service;

import java.time.LocalDateTime;

public interface TaskTimeoutService {
    int processTimeouts(LocalDateTime now);
}
