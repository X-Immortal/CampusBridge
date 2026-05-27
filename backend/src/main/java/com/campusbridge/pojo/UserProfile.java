package com.campusbridge.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
