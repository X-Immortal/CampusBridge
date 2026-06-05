package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Review;

import java.util.List;

public interface ReviewService {
    Review createReview(Long reviewerId, Long taskId, RequestDtos.CreateReviewRequest request);

    List<Review> listReviews(Long taskId);

    List<Review> listReviewsByUser(Long userId);
}
