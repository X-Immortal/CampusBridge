package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campusbridge.pojo.User;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    default User selectByUsername(String username) {
        return selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    default User selectByPhoneHash(String phoneHash) {
        return selectOne(Wrappers.<User>lambdaQuery().eq(User::getPhoneHash, phoneHash));
    }

    default int updatePassword(Long id, String passwordHash,
                       LocalDateTime updatedAt) {
        return update(null, Wrappers.<User>lambdaUpdate().eq(User::getId, id)
                .set(User::getPasswordHash, passwordHash)
                .set(User::getUpdatedAt, updatedAt));
    }

    default int updateEmail(Long id, String email,
                    LocalDateTime updatedAt) {
        return update(null, Wrappers.<User>lambdaUpdate().eq(User::getId, id)
                .set(User::getEmail, email)
                .set(User::getUpdatedAt, updatedAt));
    }

    default int updatePhone(Long id, String phoneCipher,
                    String phoneHash, LocalDateTime updatedAt) {
        return update(null, Wrappers.<User>lambdaUpdate().eq(User::getId, id)
                .set(User::getPhoneCipher, phoneCipher)
                .set(User::getPhoneHash, phoneHash)
                .set(User::getUpdatedAt, updatedAt));
    }

}
