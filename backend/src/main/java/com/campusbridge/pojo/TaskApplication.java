package com.campusbridge.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskApplication {
    private Long id;
    private Long taskId;
    private Long applicantId;
    private String message;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
