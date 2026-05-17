package com.campusbridge.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Report {
    private Long id;
    private Long reporterId;
    private String targetType;
    private Long targetId;
    private String reason;
    private String description;
    private String status;
    private LocalDateTime createdAt;
}
