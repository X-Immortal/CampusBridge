package com.campusbridge.mapper;

import com.campusbridge.enums.InteractionTargetType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campusbridge.entity.PostInteraction;
import com.campusbridge.enums.PostCategory;
import com.campusbridge.enums.PostInteractionType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostInteractionMapper extends BaseMapper<PostInteraction> {

    default PostInteraction selectByUserTargetType(
            Long userId,
            InteractionTargetType targetType,
            Long targetId,
            PostInteractionType type) {
        return selectOne(Wrappers.<PostInteraction>lambdaQuery()
                .eq(PostInteraction::getUserId, userId).eq(PostInteraction::getTargetType, targetType)
                .eq(PostInteraction::getTargetId, targetId).eq(PostInteraction::getType, type).last("LIMIT 1"));
    }

    default int deleteByUserTargetType(
            Long userId,
            InteractionTargetType targetType,
            Long targetId,
            PostInteractionType type) {
        return delete(Wrappers.<PostInteraction>lambdaQuery()
                .eq(PostInteraction::getUserId, userId).eq(PostInteraction::getTargetType, targetType)
                .eq(PostInteraction::getTargetId, targetId).eq(PostInteraction::getType, type));
    }

    default List<Long> selectTargetIdsByUserAndType(
            Long userId,
            InteractionTargetType targetType,
            PostInteractionType type) {
        return selectList(Wrappers.<PostInteraction>lambdaQuery()
                .select(PostInteraction::getTargetId)
                .eq(PostInteraction::getUserId, userId).eq(PostInteraction::getTargetType, targetType)
                .eq(PostInteraction::getType, type).orderByDesc(PostInteraction::getCreatedAt))
                .stream().map(PostInteraction::getTargetId).toList();
    }

    List<PostCategory> selectPreferredPostCategories(
            @Param("userId") Long userId,
            @Param("limit") int limit);
}
