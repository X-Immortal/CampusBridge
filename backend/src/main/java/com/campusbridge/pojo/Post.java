package com.campusbridge.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Post {
    private Long id;
    private Long authorId;
    private String authorName;
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
