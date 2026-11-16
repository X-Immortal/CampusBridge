package com.campusbridge.dto.view;

import com.campusbridge.enums.ContentItemType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentItemView {
    private ContentItemType itemType;
    private Long sourceId;
    private String title;
    private String summary;
    private Long authorId;
    private String authorName;
    private String categoryName;
    private String statusLabel;
    private LocalDateTime createdAt;
    private LocalDateTime sortTime;
    private String rewardLabel;
}
