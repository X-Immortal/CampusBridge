package com.campusbridge.dto.query;

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
    private String category;
    private String campus;
    private String status;
    private String rewardType;
    private Boolean requiresProof;
    private Boolean excludeExpired;
    private String sortBy;
    @Builder.Default
    private Integer page = 1;
    @Builder.Default
    private Integer size = 20;
    private boolean includePrivate;
}
