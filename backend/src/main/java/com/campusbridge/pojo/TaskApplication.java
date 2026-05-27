package com.campusbridge.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskApplication {
    private Long id;
    private Long taskId;
    private Long applicantId;
    private String message;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
