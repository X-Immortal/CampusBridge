package com.campusbridge.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reply {
    private Long id;
    private Long postId;
    private Long authorId;
    private Long parentReplyId;
    private String content;
    private String status;
    private Integer likeCount;
    private LocalDateTime createdAt;
    private String authorNickname;
}
