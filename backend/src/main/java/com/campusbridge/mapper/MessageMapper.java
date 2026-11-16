package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campusbridge.entity.Message;
import com.campusbridge.enums.MessageType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    Message selectOwnedById(@Param("id") Long id, @Param("ownerId") Long ownerId);

    default int countByConversationTypeAndContent(
            Long conversationId,
            MessageType type,
            String content) {
        return Math.toIntExact(selectCount(Wrappers.<Message>lambdaQuery()
                .eq(Message::getConversationId, conversationId)
                .eq(Message::getType, type).eq(Message::getContent, content)));
    }

    default int markUnreadAsRead(
            Long conversationId,
            LocalDateTime readAt) {
        return update(null, Wrappers.<Message>lambdaUpdate()
                .eq(Message::getConversationId, conversationId).isNull(Message::getReadAt)
                .set(Message::getReadAt, readAt));
    }

    int markOwnerUnreadAsRead(
            @Param("ownerId") Long ownerId,
            @Param("readAt") LocalDateTime readAt);

    long countOwnerUnread(Long ownerId);


}
