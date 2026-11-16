package com.campusbridge.dto.view;

import com.campusbridge.enums.PostCategory;

import java.time.LocalDateTime;

public record HomePostView(Long id, Long authorId, String authorName, PostCategory category, String title,
        String content, Integer replyCount, Integer likeCount, LocalDateTime createdAt) {
}
