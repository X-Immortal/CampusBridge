package com.campusbridge.controller;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.Review;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}/reviews")
public class ReviewController {

    private final CurrentUserResolver currentUserResolver;
    private final ReviewService reviewService;

    public ReviewController(CurrentUserResolver currentUserResolver, ReviewService reviewService) {
        this.currentUserResolver = currentUserResolver;
        this.reviewService = reviewService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result createReview(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId,
            @RequestBody RequestDtos.CreateReviewRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        Review review = reviewService.createReview(currentUserId, taskId, request);
        return Result.success(review);
    }

    @GetMapping
    public Result listReviews(@PathVariable Long taskId) {
        return Result.success(reviewService.listReviews(taskId));
    }
}
