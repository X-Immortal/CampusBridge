package com.campusbridge.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationRecord {
    private Long id;
    private Long reportId;
    private String targetType;
    private Long targetId;
    private Long handlerId;
    private String status;
    private String decision;
    private LocalDateTime handledAt;
}
