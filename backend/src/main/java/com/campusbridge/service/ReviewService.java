package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Review;

import java.util.List;

public interface ReviewService {
    /**
     * 创建评价
     * @param reviewerId 评价者ID
     * @param taskId 任务ID
     * @param request 评价请求
     * @return 创建的评价
     */
    Review createReview(Long reviewerId, Long taskId, RequestDtos.CreateReviewRequest request);

    /**
     * 获取任务的所有评价
     * @param taskId 任务ID
     * @return 评价列表
     */
    List<Review> listReviews(Long taskId);
}
