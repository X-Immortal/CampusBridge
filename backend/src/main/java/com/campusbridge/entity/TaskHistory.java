package com.campusbridge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.enums.TaskHistoryEventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("task_histories")
public class TaskHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private TaskStatus fromStatus;
    private TaskStatus toStatus;
    private Long operatorId;
    private TaskHistoryEventType eventType;
    private String note;
    private LocalDateTime createdAt;
}
