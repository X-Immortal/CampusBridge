package com.campusbridge.dto.view;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TaskPageView {
    private List<TaskView> tasks;
    private long total;
    private int page;
    private int size;
}
