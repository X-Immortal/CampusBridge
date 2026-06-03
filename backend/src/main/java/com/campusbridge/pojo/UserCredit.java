package com.campusbridge.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserCredit {
    private Long id;
    private Long userId;
    private Integer points;
    private Integer level;
    private Integer creditScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
