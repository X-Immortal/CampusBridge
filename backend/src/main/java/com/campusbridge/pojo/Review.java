package com.campusbridge.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Review {
    private Long id;
    private Long taskId;
    private Long reviewerId;
    private Long revieweeId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
