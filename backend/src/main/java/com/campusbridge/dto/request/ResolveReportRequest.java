package com.campusbridge.dto.request;

import com.campusbridge.enums.ModerationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResolveReportRequest {
    public ModerationStatus status;
    public String decision;
    public Long punishedUserId;
    public Integer creditChange;
    public Boolean markTaskDisputed;
}
