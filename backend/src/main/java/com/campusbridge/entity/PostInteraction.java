package com.campusbridge.entity;

import com.campusbridge.enums.InteractionTargetType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campusbridge.enums.PostInteractionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("post_interactions")
public class PostInteraction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private InteractionTargetType targetType;
    private Long targetId;
    private PostInteractionType type;
    private LocalDateTime createdAt;
}
