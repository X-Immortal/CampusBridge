package com.campusbridge.mapper;

import com.campusbridge.pojo.CreditRecord;
import com.campusbridge.pojo.UserCredit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserCreditMapper {
    /**
     * 插入用户信用信息
     */
    int insert(UserCredit entity);

    /**
     * 根据用户ID查询信用信息
     */
    UserCredit selectByUserId(@Param("userId") Long userId);

    /**
     * 更新用户信用信息
     */
    int updateById(UserCredit entity);

    /**
     * 插入信用分变化记录
     */
    int insertRecord(CreditRecord entity);

    /**
     * 查询用户信用分变化记录（分页）
     */
    List<CreditRecord> selectRecordsByUserId(
            @Param("userId") Long userId,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit);

    /**
     * 统计用户信用分变化记录总数
     */
    int countRecordsByUserId(@Param("userId") Long userId);

    /**
     * 更新用户信用分
     */
    int updateCreditScore(
            @Param("userId") Long userId,
            @Param("creditScore") Integer creditScore,
            @Param("updatedAt") LocalDateTime updatedAt);
}
