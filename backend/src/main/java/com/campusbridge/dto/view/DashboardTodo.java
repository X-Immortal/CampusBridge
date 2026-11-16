package com.campusbridge.dto.view;

import com.campusbridge.enums.DashboardTodoType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTodo {
    private DashboardTodoType type;
    private String label;
    private Long count;
    private String route;
}
