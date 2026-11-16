package com.campusbridge.dto.query;

import com.campusbridge.enums.TaskSort;
import com.campusbridge.enums.RewardType;
import com.campusbridge.enums.TaskCategory;
import com.campusbridge.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskQuery {
    private String keyword;
    private TaskCategory category;
    private String campus;
    private TaskStatus status;
    private RewardType rewardType;
    private Boolean requiresProof;
    private Boolean excludeExpired;
    private TaskSort sortBy;
    @Builder.Default
    private Integer page = 1;
    @Builder.Default
    private Integer size = 20;
    private boolean includePrivate;
}
