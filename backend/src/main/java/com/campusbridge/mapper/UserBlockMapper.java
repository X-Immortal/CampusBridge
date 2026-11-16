package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campusbridge.pojo.UserBlock;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserBlockMapper extends BaseMapper<UserBlock> {

    default int delete(Long blockerId, Long blockedId) {
        return delete(Wrappers.<UserBlock>lambdaQuery()
                .eq(UserBlock::getBlockerId, blockerId).eq(UserBlock::getBlockedId, blockedId));
    }

    default boolean exists(Long blockerId, Long blockedId) {
        return exists(Wrappers.<UserBlock>lambdaQuery()
                .eq(UserBlock::getBlockerId, blockerId).eq(UserBlock::getBlockedId, blockedId));
    }

    default boolean existsEitherDirection(Long firstUserId, Long secondUserId) {
        return exists(Wrappers.<UserBlock>lambdaQuery()
                .and(q -> q.eq(UserBlock::getBlockerId, firstUserId).eq(UserBlock::getBlockedId, secondUserId))
                .or(q -> q.eq(UserBlock::getBlockerId, secondUserId).eq(UserBlock::getBlockedId, firstUserId)));
    }

    default List<UserBlock> selectBlockedIds(Long blockerId) {
        return selectList(Wrappers.<UserBlock>lambdaQuery().eq(UserBlock::getBlockerId, blockerId)
                .orderByDesc(UserBlock::getCreatedAt));
    }

}
