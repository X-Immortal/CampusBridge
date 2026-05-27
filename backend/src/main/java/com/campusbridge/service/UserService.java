package com.campusbridge.service;

import com.campusbridge.pojo.User;

import java.util.List;

public interface UserService {
    int insert(User user);

    User selectById(Long id);

    List<User> selectAll();

    int updateById(User user);

    int deleteById(Long id);
}
