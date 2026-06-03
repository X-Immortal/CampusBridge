package com.campusbridge.mapper;

import com.campusbridge.pojo.RewardSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RewardSettlementMapper {
    int insert(RewardSettlement entity);

    RewardSettlement selectByTaskId(@Param("taskId") Long taskId);

    List<RewardSettlement> selectByUserId(
            @Param("userId") Long userId,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit);

    int countByUserId(@Param("userId") Long userId);
}
