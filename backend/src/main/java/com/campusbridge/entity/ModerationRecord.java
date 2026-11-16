package com.campusbridge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campusbridge.enums.ReportStatus;
import com.campusbridge.enums.ReportTargetType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("moderation_records")
public class ModerationRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private ReportTargetType targetType;
    private Long targetId;
    private Long handlerId;
    private ReportStatus status;
    private String decision;
    private LocalDateTime handledAt;
}
