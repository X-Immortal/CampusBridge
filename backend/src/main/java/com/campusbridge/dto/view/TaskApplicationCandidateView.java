package com.campusbridge.dto.view;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskApplicationCandidateView {
    private Long applicationId;
    private Long applicantId;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private String campus;
    private String college;
    private Boolean verified;
    private Integer creditLevel;
    private Integer points;
    private String message;
    private String status;
    private LocalDateTime createdAt;
}
