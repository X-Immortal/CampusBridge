package com.campusbridge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_blocks")
public class UserBlock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long blockerId;
    private Long blockedId;
    private LocalDateTime createdAt;
}
