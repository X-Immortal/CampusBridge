package com.campusbridge.mapper;

import com.campusbridge.pojo.UserBlock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserBlockMapper {
    int insert(UserBlock entity);

    int delete(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    boolean exists(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    List<UserBlock> selectBlockedIds(@Param("blockerId") Long blockerId);

    List<UserBlock> selectBlockerIds(@Param("blockedId") Long blockedId);
}
