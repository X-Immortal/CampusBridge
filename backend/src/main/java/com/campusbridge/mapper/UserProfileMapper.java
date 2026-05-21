package com.campusbridge.mapper;

import com.campusbridge.pojo.UserProfile;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserProfileMapper {
    int insert(UserProfile entity);

    UserProfile selectById(Long id);

    UserProfile selectByUserId(Long userId);

    List<UserProfile> selectAll();

    int updateById(UserProfile entity);

    int deleteById(Long id);
}
