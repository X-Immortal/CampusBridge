package com.campusbridge.entity;

import com.campusbridge.enums.CreditSourceType;
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
@TableName("credit_records")
public class CreditRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer changeValue;
    private String reason;
    private CreditSourceType sourceType;
    private Long sourceId;
    private LocalDateTime createdAt;
}
