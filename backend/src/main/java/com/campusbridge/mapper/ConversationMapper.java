package com.campusbridge.mapper;

import com.campusbridge.pojo.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ConversationMapper {
    int insert(Conversation entity);

    Conversation selectById(Long id);

    List<Conversation> selectAll();

    List<Conversation> selectByOwnerId(
            @Param("ownerId") Long ownerId,
            @Param("offset") Integer offset,
            @Param("size") Integer size);

    long countByOwnerId(Long ownerId);

    Conversation selectOwnedById(
            @Param("id") Long id,
            @Param("ownerId") Long ownerId);

    Conversation selectByOwnerAndRelated(
            @Param("ownerId") Long ownerId,
            @Param("type") String type,
            @Param("relatedType") String relatedType,
            @Param("relatedId") Long relatedId);

    Conversation selectPrivateByOwnerAndPeer(
            @Param("ownerId") Long ownerId,
            @Param("peerUserId") Long peerUserId);

    int updateById(Conversation entity);

    int updateLastMessageAt(
            @Param("id") Long id,
            @Param("peerUserId") Long peerUserId,
            @Param("lastMessageAt") LocalDateTime lastMessageAt);

    int deleteById(Long id);
}
