package com.campusbridge.service;

import com.campusbridge.dto.request.CreateReviewRequest;
import com.campusbridge.entity.Review;

import java.util.List;

public interface ReviewService {
    Review createReview(Long reviewerId, Long taskId, CreateReviewRequest request);

    List<Review> listReviews(Long taskId);

    List<Review> listReviewsByUser(Long userId);
}
