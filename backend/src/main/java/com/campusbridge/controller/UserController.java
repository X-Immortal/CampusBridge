package com.campusbridge.controller;

import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Result;
import com.campusbridge.service.ReviewService;
import com.campusbridge.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final CurrentUserResolver currentUserResolver;
    private final UserProfileService userProfileService;
    private final ReviewService reviewService;

    public UserController(
            CurrentUserResolver currentUserResolver,
            UserProfileService userProfileService,
            ReviewService reviewService) {
        this.currentUserResolver = currentUserResolver;
        this.userProfileService = userProfileService;
        this.reviewService = reviewService;
    }

    @GetMapping("/me")
    public Result getCurrentUser(HttpServletRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(request);
        return Result.success(userProfileService.getCurrentUser(currentUserId));
    }

    @GetMapping("/me/profile")
    public Result getMyProfile(HttpServletRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(request);
        return Result.success(userProfileService.getMyProfile(currentUserId));
    }

    @PutMapping("/me/profile")
    public Result updateMyProfile(
            HttpServletRequest request,
            @RequestBody RequestDtos.UpdateUserProfileRequest requestBody) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(request);
        return Result.success(userProfileService.updateMyProfile(currentUserId, requestBody));
    }

    @GetMapping("/{userId}/profile")
    public Result getUserHomepage(@PathVariable Long userId) {
        return Result.success(userProfileService.getUserHomepage(userId));
    }

    @PostMapping("/me/avatar")
    public Result uploadAvatar(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(request);
        String avatarUrl = userProfileService.updateAvatar(currentUserId, file);
        return Result.success(Map.of("avatarUrl", avatarUrl));
    }

    @GetMapping("/{userId}/reviews")
    public Result getUserReviews(@PathVariable Long userId) {
        return Result.success(reviewService.listReviewsByUser(userId));
    }
}
