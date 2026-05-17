package com.campusbridge.mapper;

import com.campusbridge.pojo.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserMapper {
    int insert(User entity);

    User selectById(Long id);

    User selectByUsername(String username);

    User selectByPhoneHash(String phoneHash);

    List<User> selectAll();

    int updateById(User entity);

    int deleteById(Long id);
}
