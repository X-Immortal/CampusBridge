package com.campusbridge.mapper;

import com.campusbridge.pojo.UserCredit;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserCreditMapper {
    int insert(UserCredit entity);

    UserCredit selectById(Long id);

    UserCredit selectByUserId(Long userId);

    List<UserCredit> selectAll();

    int updateById(UserCredit entity);

    int deleteById(Long id);
}
