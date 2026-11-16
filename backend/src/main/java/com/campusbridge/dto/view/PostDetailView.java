package com.campusbridge.dto.view;

import com.campusbridge.entity.Attachment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostDetailView {
    private PostView post;
    private List<ReplyView> replies;
    private Boolean likedByCurrentUser;
    private Boolean markedByCurrentUser;
    private Long acceptedReplyId;
    private List<Attachment> attachments;
}
