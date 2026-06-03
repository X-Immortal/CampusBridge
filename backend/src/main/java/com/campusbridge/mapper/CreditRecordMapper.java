package com.campusbridge.mapper;

import com.campusbridge.pojo.CreditRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CreditRecordMapper {
    int insert(CreditRecord entity);

    CreditRecord selectById(Long id);

    List<CreditRecord> selectAll();

    int updateById(CreditRecord entity);

    int deleteById(Long id);

    /**
     * 查询用户信用分变化记录（分页）
     */
    List<CreditRecord> selectByUserId(
            @Param("userId") Long userId,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit);

    /**
     * 统计用户信用分变化记录总数
     */
    int countByUserId(@Param("userId") Long userId);
}
