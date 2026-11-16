package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.dto.request.CreateReviewRequest;
import com.campusbridge.entity.Review;
import com.campusbridge.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final CurrentUserResolver currentUserResolver;
    private final ReviewService reviewService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Review> createReview(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @Valid @RequestBody CreateReviewRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        return ApiResponse.success(reviewService.createReview(currentUserId, taskId, request));
    }

    @GetMapping
    public ApiResponse<List<Review>> listReviews(@PathVariable Long taskId) {
        return ApiResponse.success(reviewService.listReviews(taskId));
    }
}
