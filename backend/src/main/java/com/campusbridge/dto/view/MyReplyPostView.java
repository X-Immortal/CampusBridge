package com.campusbridge.dto.view;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyReplyPostView {
    private PostView post;
    private List<ReplyView> replies;
    private LocalDateTime lastReplyAt;
    private Long replyTotal;
}
