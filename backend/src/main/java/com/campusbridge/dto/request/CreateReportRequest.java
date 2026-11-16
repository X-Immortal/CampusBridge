package com.campusbridge.dto.request;

import com.campusbridge.enums.ReportTargetType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {
    public ReportTargetType targetType;
    public Long targetId;
    public String reason;
    public String description;
}
