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
@TableName("attachments")
public class Attachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long uploaderId;
    private String ownerType;
    private Long ownerId;
    private String type;
    private String fileName;
    private String fileUrl;
    private String contentType;
    private Long size;
    private String status;
    private LocalDateTime createdAt;
}
