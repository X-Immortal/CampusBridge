package com.campusbridge.dto.view;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileView {
    private Long userId;
    private String nickname;
    private String bio;
    private String campus;
    private String college;
    private String avatarUrl;
    private String contactInfo;
    private List<String> interestTags;
    private List<String> skillTags;
    private Boolean verified;
    private Integer creditLevel;
    private Integer points;
}
