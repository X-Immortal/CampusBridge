package com.campusbridge.mapper;

import com.campusbridge.pojo.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MessageMapper {
    int insert(Message entity);

    Message selectById(Long id);

    List<Message> selectAll();

    List<Message> selectByConversationId(
            @Param("conversationId") Long conversationId,
            @Param("offset") Integer offset,
            @Param("size") Integer size);

    long countByConversationId(Long conversationId);

    int countByConversationTypeAndContent(
            @Param("conversationId") Long conversationId,
            @Param("type") String type,
            @Param("content") String content);

    int markUnreadAsRead(
            @Param("conversationId") Long conversationId,
            @Param("readAt") LocalDateTime readAt);

    int markOwnerUnreadAsRead(
            @Param("ownerId") Long ownerId,
            @Param("readAt") LocalDateTime readAt);

    long countOwnerUnread(Long ownerId);

    int updateById(Message entity);

    int deleteById(Long id);
}
