package com.campusbridge.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Reply {
    private Long id;
    private Long postId;
    private Long authorId;
    private Long parentReplyId;
    private String content;
    private String status;
    private Integer likeCount;
    private LocalDateTime createdAt;
}
