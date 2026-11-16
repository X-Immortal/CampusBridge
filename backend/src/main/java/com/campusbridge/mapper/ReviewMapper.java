package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campusbridge.pojo.Review;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    default List<Review> selectByTaskId(Long taskId) {
        return selectList(Wrappers.<Review>lambdaQuery().eq(Review::getTaskId, taskId)
                .orderByAsc(Review::getCreatedAt, Review::getId));
    }

    default List<Review> selectByRevieweeId(Long revieweeId) {
        return selectList(Wrappers.<Review>lambdaQuery().eq(Review::getRevieweeId, revieweeId)
                .orderByDesc(Review::getCreatedAt));
    }

}
