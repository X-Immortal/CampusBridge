package com.campusbridge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.campusbridge.enums.ContentItemType;
import com.campusbridge.enums.NotificationType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notifications")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long receiverId;
    private NotificationType type;
    private String title;
    private String content;
    private ContentItemType relatedType;
    private Long relatedId;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
