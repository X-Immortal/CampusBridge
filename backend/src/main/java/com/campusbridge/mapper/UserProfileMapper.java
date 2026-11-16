package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campusbridge.pojo.UserProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {

    default UserProfile selectByUserId(Long userId) {
        return selectOne(Wrappers.<UserProfile>lambdaQuery().eq(UserProfile::getUserId, userId));
    }

}
