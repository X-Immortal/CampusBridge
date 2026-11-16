package com.campusbridge.entity;

import com.campusbridge.enums.ContentItemType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campusbridge.enums.ConversationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("conversations")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private ConversationType type;
    private String title;
    private Long ownerId;
    private Long peerUserId;
    private ContentItemType relatedType;
    private Long relatedId;
    private Integer replyable;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
