package com.campusbridge.dto.view;

import com.campusbridge.enums.ReplyStatus;
import com.campusbridge.entity.Reply;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class ReplyView {
    private Long id;
    private Long postId;
    private Long authorId;
    private Long parentReplyId;
    private String content;
    private ReplyStatus status;
    private Integer likeCount;
    private LocalDateTime createdAt;
    private String authorName;

    public static ReplyView from(Reply entity) {
        if (entity == null) {
            return null;
        }
        ReplyView view = new ReplyView();
        view.id = entity.getId();
        view.postId = entity.getPostId();
        view.authorId = entity.getAuthorId();
        view.parentReplyId = entity.getParentReplyId();
        view.content = entity.getContent();
        view.status = entity.getStatus();
        view.likeCount = entity.getLikeCount();
        view.createdAt = entity.getCreatedAt();
        return view;
    }
}
