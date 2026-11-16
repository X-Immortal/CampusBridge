package com.campusbridge.dto.view;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummary {
    private Long userTotal;
    private Long verifiedUserTotal;
    private Long taskTotal;
    private Long activeTaskTotal;
    private Long completedTaskTotal;
    private Long postTotal;
    private Long pendingVerificationTotal;
    private Long pendingReportTotal;
    private Long disputedTaskTotal;
    private LocalDateTime updatedAt;
}
