package com.campusbridge.mapper;

import com.campusbridge.pojo.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReviewMapper {
    int insert(Review entity);

    Review selectById(Long id);

    List<Review> selectAll();

    int updateById(Review entity);

    int deleteById(Long id);

    /**
     * 根据任务ID查询所有评价
     */
    List<Review> selectByTaskId(@Param("taskId") Long taskId);

    /**
     * 根据任务ID、评价者和被评价者查询评价
     */
    Review selectByTaskAndReviewer(
            @Param("taskId") Long taskId,
            @Param("reviewerId") Long reviewerId,
            @Param("revieweeId") Long revieweeId);
}
