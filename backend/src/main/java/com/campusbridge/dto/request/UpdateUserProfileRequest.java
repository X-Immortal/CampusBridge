package com.campusbridge.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {
    public String nickname;
    public String bio;
    public String campus;
    public String college;
    public String contactInfo;
    public String avatarUrl;
    public List<String> interestTags;
    public List<String> skillTags;
}
