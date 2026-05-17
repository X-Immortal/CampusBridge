package com.campusbridge.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Post {
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
