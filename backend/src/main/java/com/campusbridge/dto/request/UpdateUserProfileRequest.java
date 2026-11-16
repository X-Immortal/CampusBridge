package com.campusbridge.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {
    @Size(max = 50, message = "nickname is too long")
    public String nickname;

    @Size(max = 500, message = "bio is too long")
    public String bio;

    @Size(max = 100, message = "campus is too long")
    public String campus;

    @Size(max = 100, message = "college is too long")
    public String college;

    @Size(max = 255, message = "contactInfo is too long")
    public String contactInfo;

    @Size(max = 500, message = "avatarUrl is too long")
    public String avatarUrl;

    @Size(max = 20, message = "interestTags count exceeds limit")
    public List<@Size(max = 30, message = "tag length exceeds limit") String> interestTags;

    @Size(max = 20, message = "skillTags count exceeds limit")
    public List<@Size(max = 30, message = "tag length exceeds limit") String> skillTags;
}
