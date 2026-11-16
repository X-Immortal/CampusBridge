package com.campusbridge.service;

import com.campusbridge.dto.request.UpdateUserProfileRequest;
import com.campusbridge.dto.view.UserHomepageView;
import com.campusbridge.dto.view.UserProfileView;
import com.campusbridge.dto.view.UserView;
import org.springframework.web.multipart.MultipartFile;

public interface UserProfileService {
    UserView getCurrentUser(Long currentUserId);

    UserProfileView getMyProfile(Long currentUserId);

    UserProfileView updateMyProfile(Long currentUserId, UpdateUserProfileRequest request);

    UserHomepageView getUserHomepage(Long userId);

    String updateAvatar(Long currentUserId, MultipartFile file);
}
