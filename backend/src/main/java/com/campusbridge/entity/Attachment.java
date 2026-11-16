package com.campusbridge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campusbridge.enums.AttachmentOwnerType;
import com.campusbridge.enums.AttachmentStatus;
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
    private AttachmentOwnerType ownerType;
    private Long ownerId;
    private String fileName;
    private String fileUrl;
    private String contentType;
    private Long size;
    private AttachmentStatus status;
    private LocalDateTime createdAt;
}
