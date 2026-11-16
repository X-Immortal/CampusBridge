package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.dto.request.UpdateUserProfileRequest;
import com.campusbridge.dto.view.AvatarView;
import com.campusbridge.dto.view.UserHomepageView;
import com.campusbridge.dto.view.UserProfileView;
import com.campusbridge.dto.view.UserView;
import com.campusbridge.entity.Review;
import com.campusbridge.service.ReviewService;
import com.campusbridge.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final CurrentUserResolver currentUserResolver;
    private final UserProfileService userProfileService;
    private final ReviewService reviewService;

    @GetMapping("/me")
    public ApiResponse<UserView> getCurrentUser(HttpServletRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(request);
        return ApiResponse.success(userProfileService.getCurrentUser(currentUserId));
    }

    @GetMapping("/me/profile")
    public ApiResponse<UserProfileView> getMyProfile(HttpServletRequest request) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(request);
        return ApiResponse.success(userProfileService.getMyProfile(currentUserId));
    }

    @PutMapping("/me/profile")
    public ApiResponse<UserProfileView> updateMyProfile(
            HttpServletRequest request,
            @RequestBody UpdateUserProfileRequest requestBody) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(request);
        return ApiResponse.success(userProfileService.updateMyProfile(currentUserId, requestBody));
    }

    @GetMapping("/{userId}/profile")
    public ApiResponse<UserHomepageView> getUserHomepage(@PathVariable Long userId) {
        return ApiResponse.success(userProfileService.getUserHomepage(userId));
    }

    @PostMapping("/me/avatar")
    public ApiResponse<AvatarView> uploadAvatar(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(request);
        String avatarUrl = userProfileService.updateAvatar(currentUserId, file);
        return ApiResponse.success(new AvatarView(avatarUrl));
    }

    @GetMapping("/{userId}/reviews")
    public ApiResponse<List<Review>> getUserReviews(@PathVariable Long userId) {
        return ApiResponse.success(reviewService.listReviewsByUser(userId));
    }
}
