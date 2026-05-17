package com.campusbridge.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Review {
    private Long id;
    private Long taskId;
    private Long reviewerId;
    private Long revieweeId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
