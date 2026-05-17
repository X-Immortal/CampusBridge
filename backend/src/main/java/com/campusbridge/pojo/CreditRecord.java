package com.campusbridge.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CreditRecord {
    private Long id;
    private Long userId;
    private Integer changeValue;
    private String reason;
    private String sourceType;
    private Long sourceId;
    private LocalDateTime createdAt;
}
