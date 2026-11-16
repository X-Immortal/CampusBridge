package com.campusbridge.mapper;

import com.campusbridge.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

import java.util.List;

@Mapper
public interface UserMapper {
    int insert(User entity);

    User selectById(Long id);

    User selectByUsername(String username);

    User selectByPhoneHash(String phoneHash);

    List<User> selectAll();

    int updateById(User entity);

    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash,
                       @Param("updatedAt") LocalDateTime updatedAt);

    int updateEmail(@Param("id") Long id, @Param("email") String email,
                    @Param("updatedAt") LocalDateTime updatedAt);

    int updatePhone(@Param("id") Long id, @Param("phoneCipher") String phoneCipher,
                    @Param("phoneHash") String phoneHash, @Param("updatedAt") LocalDateTime updatedAt);

    int deleteById(Long id);
}
