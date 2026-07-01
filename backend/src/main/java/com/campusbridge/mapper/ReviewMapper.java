package com.campusbridge.mapper;

import com.campusbridge.pojo.Review;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReviewMapper {
    int insert(Review entity);

    Review selectById(Long id);

    List<Review> selectAll();

    List<Review> selectByTaskId(Long taskId);

    List<Review> selectByRevieweeId(Long revieweeId);

    int updateById(Review entity);

    int deleteById(Long id);
}
