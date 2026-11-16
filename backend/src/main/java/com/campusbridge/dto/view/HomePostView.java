package com.campusbridge.dto.view;

import java.time.LocalDateTime;

public record HomePostView(Long id, Long authorId, String authorName, String category, String title,
        String content, Integer replyCount, Integer likeCount, LocalDateTime createdAt) {
}
