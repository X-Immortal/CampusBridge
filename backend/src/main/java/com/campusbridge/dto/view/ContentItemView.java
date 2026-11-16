package com.campusbridge.dto.view;

import com.campusbridge.enums.ContentItemType;
import com.campusbridge.enums.RewardType;
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
    private String category;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime sortTime;
    private RewardType rewardType;
    private Integer rewardAmount;
    private String rewardDescription;
}
