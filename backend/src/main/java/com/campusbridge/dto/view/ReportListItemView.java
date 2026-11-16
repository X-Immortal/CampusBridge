package com.campusbridge.dto.view;

import com.campusbridge.entity.Report;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportListItemView {
    private Report report;
    private String handlerName;
}
