package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusbridge.dto.view.ConversationView;
import com.campusbridge.entity.Conversation;
import com.campusbridge.enums.ConversationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    Page<ConversationView> selectByOwnerId(Page<ConversationView> page, @Param("ownerId") Long ownerId);

    default Conversation selectOwnedById(Long id, Long ownerId) {
        return selectOne(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getId, id).eq(Conversation::getOwnerId, ownerId));
    }

    ConversationView selectOwnedViewById(@Param("id") Long id, @Param("ownerId") Long ownerId);

    default Conversation selectPrivateByOwnerAndPeer(Long ownerId, Long peerUserId) {
        return selectOne(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getOwnerId, ownerId)
                .eq(Conversation::getPeerUserId, peerUserId)
                .eq(Conversation::getType, ConversationType.PRIVATE)
                .eq(Conversation::getReplyable, 1)
                .orderByAsc(Conversation::getId).last("LIMIT 1"));
    }

    default Conversation selectPrivateByOwnerAndPeerCurrent(Long ownerId, Long peerUserId) {
        return selectOne(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getOwnerId, ownerId)
                .eq(Conversation::getPeerUserId, peerUserId)
                .eq(Conversation::getType, ConversationType.PRIVATE)
                .eq(Conversation::getReplyable, 1)
                .last("LIMIT 1 FOR UPDATE"));
    }

    int updateLastMessageAt(
            @Param("id") Long id,
            @Param("peerUserId") Long peerUserId,
            @Param("lastMessageAt") LocalDateTime lastMessageAt);

}
