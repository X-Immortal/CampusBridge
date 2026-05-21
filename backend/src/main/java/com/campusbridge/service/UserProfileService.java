package com.campusbridge.service;

import com.campusbridge.dto.view.ViewDtos;
import com.campusbridge.dto.request.RequestDtos;

public interface UserProfileService {
    ViewDtos.UserView getCurrentUser(Long currentUserId);

    ViewDtos.UserProfileView getMyProfile(Long currentUserId);

    ViewDtos.UserProfileView updateMyProfile(Long currentUserId, RequestDtos.UpdateUserProfileRequest request);

    ViewDtos.UserHomepageView getUserHomepage(Long userId);
}
