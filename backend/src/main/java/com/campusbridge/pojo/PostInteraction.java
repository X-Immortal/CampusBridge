package com.campusbridge.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostInteraction {
    private Long id;
    private Long userId;
    private String targetType;
    private Long targetId;
    private String type;
    private LocalDateTime createdAt;
}
