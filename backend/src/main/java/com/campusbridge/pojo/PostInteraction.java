package com.campusbridge.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PostInteraction {
    private Long id;
    private Long userId;
    private String targetType;
    private Long targetId;
    private String type;
    private LocalDateTime createdAt;
}
