package com.campusbridge.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentQuery {
    private boolean includePosts;
    private boolean includeTasks;
    private String keyword;
    private String categoryCode;
    private String campus;
    private String locationKeyword;
    private String status;
    private String rewardType;
    private boolean orderByDeadline;
    private long offset;
    private int limit;
}
