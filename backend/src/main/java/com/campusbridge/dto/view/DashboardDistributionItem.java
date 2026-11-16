package com.campusbridge.dto.view;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDistributionItem {
    private String status;
    private String label;
    private Long count;
}
