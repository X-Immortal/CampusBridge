package com.campusbridge.dto.view;

import com.campusbridge.entity.TaskApplication;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyTaskApplicationView {
    private TaskApplication application;
    private TaskView task;
}
