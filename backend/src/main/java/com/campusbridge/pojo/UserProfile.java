package com.campusbridge.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_profiles")
public class UserProfile {
    @TableId(type = IdType.AUTO)
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
