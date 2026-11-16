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
@TableName("posts")
public class Post {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;
    private String category;
    private String title;
    private String content;
    private String status;
    private Integer replyCount;
    private Integer likeCount;
    private Integer markCount;
    private Long acceptedReplyId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
