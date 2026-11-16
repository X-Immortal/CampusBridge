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
@TableName("campus_verifications")
public class CampusVerification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String method;
    private String realNameCipher;
    private String studentNumberCipher;
    private String studentNumberHash;
    private String school;
    private String department;
    private String evidenceUrl;
    private String status;
    private Long reviewerId;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
