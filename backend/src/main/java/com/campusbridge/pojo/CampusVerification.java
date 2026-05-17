package com.campusbridge.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CampusVerification {
    private Long id;
    private Long userId;
    private String method;
    private String realNameCipher;
    private String studentNumberCipher;
    private String studentNumberHash;
    private String school;
    private String department;
    private String evidenceUrl;
    private String status;
    private Long reviewerId;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
