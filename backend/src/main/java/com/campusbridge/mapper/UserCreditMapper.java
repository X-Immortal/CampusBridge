package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campusbridge.entity.UserCredit;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserCreditMapper extends BaseMapper<UserCredit> {

    int initializeForUser(Long userId);

    default UserCredit selectByUserId(Long userId) {
        return selectOne(Wrappers.<UserCredit>lambdaQuery().eq(UserCredit::getUserId, userId));
    }

    default UserCredit selectByUserIdForUpdate(Long userId) {
        return selectOne(Wrappers.<UserCredit>lambdaQuery()
                .eq(UserCredit::getUserId, userId).last("FOR UPDATE"));
    }

}
