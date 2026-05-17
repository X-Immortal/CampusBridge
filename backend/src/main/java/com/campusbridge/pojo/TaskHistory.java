package com.campusbridge.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskHistory {
    private Long id;
    private Long taskId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String note;
    private LocalDateTime createdAt;
}
