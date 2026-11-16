package com.campusbridge.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("moderation_records")
public class ModerationRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private String targetType;
    private Long targetId;
    private Long handlerId;
    private String status;
    private String decision;
    private LocalDateTime handledAt;
}
