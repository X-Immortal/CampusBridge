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
@TableName("conversations")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String title;
    private Long ownerId;
    private Long peerUserId;
    private String relatedType;
    private Long relatedId;
    private Integer replyable;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
