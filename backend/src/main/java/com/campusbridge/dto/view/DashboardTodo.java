package com.campusbridge.dto.view;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTodo {
    private String type;
    private String label;
    private Long count;
    private String route;
}
