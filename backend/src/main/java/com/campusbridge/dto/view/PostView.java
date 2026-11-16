package com.campusbridge.dto.view;

import com.campusbridge.pojo.Post;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class PostView {
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

    public static PostView from(Post entity) {
        if (entity == null) {
            return null;
        }
        PostView view = new PostView();
        view.id = entity.getId();
        view.authorId = entity.getAuthorId();
        view.category = entity.getCategory();
        view.title = entity.getTitle();
        view.content = entity.getContent();
        view.status = entity.getStatus();
        view.replyCount = entity.getReplyCount();
        view.likeCount = entity.getLikeCount();
        view.markCount = entity.getMarkCount();
        view.acceptedReplyId = entity.getAcceptedReplyId();
        view.createdAt = entity.getCreatedAt();
        view.updatedAt = entity.getUpdatedAt();
        return view;
    }
}
