package com.campusbridge.entity;

import com.campusbridge.enums.ReplyStatus;
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
@TableName("replies")
public class Reply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long authorId;
    private Long parentReplyId;
    private String content;
    private ReplyStatus status;
    private Integer likeCount;
    private LocalDateTime createdAt;
}
