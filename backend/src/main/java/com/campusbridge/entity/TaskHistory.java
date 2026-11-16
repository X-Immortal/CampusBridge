package com.campusbridge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String note;
    private LocalDateTime createdAt;
}
