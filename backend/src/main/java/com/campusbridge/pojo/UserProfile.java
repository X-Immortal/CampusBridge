package com.campusbridge.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserProfile {
    private Long id;
    private Long userId;
    private String nickname;
    private String bio;
    private String campus;
    private String college;
    private String contactInfo;
    private String avatarUrl;
    private String interestTags;
    private String skillTags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
