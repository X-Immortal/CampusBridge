package com.campusbridge.controller;

import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.Review;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result createReview(
            @PathVariable Long taskId,
            @RequestBody Review request) {
        return Result.success();
    }

    @GetMapping
    public Result listReviews(@PathVariable Long taskId) {
        return Result.success();
    }
}
