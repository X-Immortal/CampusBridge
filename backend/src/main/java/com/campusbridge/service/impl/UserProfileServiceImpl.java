package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.view.ViewDtos;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.mapper.UserCreditMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.mapper.UserProfileMapper;
import com.campusbridge.pojo.CampusVerification;
import com.campusbridge.pojo.User;
import com.campusbridge.pojo.UserCredit;
import com.campusbridge.pojo.UserProfile;
import com.campusbridge.service.UserProfileService;
import com.campusbridge.utils.AliOSSUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class UserProfileServiceImpl implements UserProfileService {
    private static final int MAX_NICKNAME_LENGTH = 50;
    private static final int MAX_BIO_LENGTH = 500;
    private static final int MAX_CAMPUS_LENGTH = 100;
    private static final int MAX_COLLEGE_LENGTH = 100;
    private static final int MAX_CONTACT_INFO_LENGTH = 255;
    private static final int MAX_AVATAR_URL_LENGTH = 500;
    private static final int MAX_TAG_COUNT = 20;
    private static final int MAX_TAG_LENGTH = 30;
    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_AVATAR_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp");

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final ObjectMapper objectMapper;
    private final UserCreditMapper userCreditMapper;
    private final CampusVerificationMapper campusVerificationMapper;
    private final AliOSSUtils aliOSSUtils;

    public UserProfileServiceImpl(
            UserMapper userMapper,
            UserProfileMapper userProfileMapper,
            ObjectMapper objectMapper,
            UserCreditMapper userCreditMapper,
            CampusVerificationMapper campusVerificationMapper,
            AliOSSUtils aliOSSUtils) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.objectMapper = objectMapper;
        this.userCreditMapper = userCreditMapper;
        this.campusVerificationMapper = campusVerificationMapper;
        this.aliOSSUtils = aliOSSUtils;
    }

    @Override
    public ViewDtos.UserView getCurrentUser(Long currentUserId) {
        User user = requireUser(currentUserId);
        return toUserView(user);
    }

    @Override
    public ViewDtos.UserProfileView getMyProfile(Long currentUserId) {
        requireUser(currentUserId);
        UserProfile profile = getOrCreateProfile(currentUserId);
        return toProfileView(profile, currentUserId);
    }

    @Override
    public ViewDtos.UserProfileView updateMyProfile(
            Long currentUserId,
            RequestDtos.UpdateUserProfileRequest request) {
        requireUser(currentUserId);
        validateProfileRequest(request);

        UserProfile profile = getOrCreateProfile(currentUserId);
        profile.setNickname(request.getNickname());
        profile.setBio(request.getBio());
        profile.setCampus(request.getCampus());
        profile.setCollege(request.getCollege());
        profile.setContactInfo(request.getContactInfo());
        profile.setAvatarUrl(request.getAvatarUrl());
        profile.setInterestTags(toJson(request.getInterestTags()));
        profile.setSkillTags(toJson(request.getSkillTags()));
        profile.setUpdatedAt(LocalDateTime.now());

        int affectedRows = userProfileMapper.updateById(profile);
        if (affectedRows != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to update user profile");
        }

        return toProfileView(profile, currentUserId);
    }

    @Override
    public ViewDtos.UserHomepageView getUserHomepage(Long userId) {
        User user = requireUser(userId);
        UserProfile profile = getProfileOrEmpty(user.getId());
        UserCredit credit = userCreditMapper.selectByUserId(user.getId());
        CampusVerification verification =
                campusVerificationMapper.selectLatestByUserId(user.getId());

        int points = credit == null || credit.getPoints() == null
                ? 0
                : credit.getPoints();
        int creditLevel = credit == null || credit.getLevel() == null
                ? 1
                : credit.getLevel();
        boolean verified = verification != null
                && "VERIFIED".equals(verification.getStatus());

        return new ViewDtos.UserHomepageView(
                profile.getUserId(),
                profile.getNickname(),
                profile.getAvatarUrl(),
                profile.getBio(),
                profile.getCampus(),
                profile.getCollege(),
                verified,
                creditLevel,
                points,
                parseTags(profile.getInterestTags()),
                parseTags(profile.getSkillTags()));
    }

    @Override
    public String updateAvatar(Long currentUserId, MultipartFile file) {
        requireUser(currentUserId);

        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "file must not be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(
                    ErrorCode.FILE_002_UNSUPPORTED_TYPE,
                    "avatar must be PNG, JPEG or WebP");
        }

        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BusinessException(
                    ErrorCode.FILE_001_TOO_LARGE,
                    "avatar must not exceed 5MB");
        }

        String avatarUrl;
        try {
            avatarUrl = aliOSSUtils.upload(file);
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to upload avatar to OSS");
        }

        UserProfile profile = getOrCreateProfile(currentUserId);
        profile.setAvatarUrl(avatarUrl);
        profile.setUpdatedAt(LocalDateTime.now());

        int affectedRows = userProfileMapper.updateById(profile);
        if (affectedRows != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to update avatar URL in profile");
        }

        return avatarUrl;
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "userId must not be null");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_001_NOT_FOUND);
        }
        return user;
    }

    private UserProfile getOrCreateProfile(Long userId) {
        UserProfile profile = userProfileMapper.selectByUserId(userId);
        if (profile != null) {
            return profile;
        }

        LocalDateTime now = LocalDateTime.now();
        UserProfile newProfile = new UserProfile();
        newProfile.setUserId(userId);
        newProfile.setInterestTags("[]");
        newProfile.setSkillTags("[]");
        newProfile.setCreatedAt(now);
        newProfile.setUpdatedAt(now);

        int affectedRows = userProfileMapper.insert(newProfile);
        if (affectedRows != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create user profile");
        }
        return newProfile;
    }

    private UserProfile getProfileOrEmpty(Long userId) {
        UserProfile profile = userProfileMapper.selectByUserId(userId);
        if (profile != null) {
            return profile;
        }

        UserProfile emptyProfile = new UserProfile();
        emptyProfile.setUserId(userId);
        emptyProfile.setInterestTags("[]");
        emptyProfile.setSkillTags("[]");
        return emptyProfile;
    }

    private ViewDtos.UserView toUserView(User user) {
        return new ViewDtos.UserView(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getEnabled() != null && user.getEnabled() == 1,
                user.getCreatedAt());
    }

    private ViewDtos.UserProfileView toProfileView(UserProfile profile, Long userId) {
        UserCredit credit = userCreditMapper.selectByUserId(userId);
        CampusVerification verification =
                campusVerificationMapper.selectLatestByUserId(userId);

        int points = credit == null || credit.getPoints() == null
                ? 0
                : credit.getPoints();
        int creditLevel = credit == null || credit.getLevel() == null
                ? 1
                : credit.getLevel();
        boolean verified = verification != null
                && "VERIFIED".equals(verification.getStatus());

        return new ViewDtos.UserProfileView(
                profile.getUserId(),
                profile.getNickname(),
                profile.getBio(),
                profile.getCampus(),
                profile.getCollege(),
                profile.getAvatarUrl(),
                profile.getContactInfo(),
                parseTags(profile.getInterestTags()),
                parseTags(profile.getSkillTags()),
                verified,
                creditLevel,
                points);
    }

    private void validateProfileRequest(RequestDtos.UpdateUserProfileRequest request) {
        if (request == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "Request body must not be null");
        }

        validateLength(request.getNickname(), MAX_NICKNAME_LENGTH, "nickname is too long");
        validateLength(request.getBio(), MAX_BIO_LENGTH, "bio is too long");
        validateLength(request.getCampus(), MAX_CAMPUS_LENGTH, "campus is too long");
        validateLength(request.getCollege(), MAX_COLLEGE_LENGTH, "college is too long");
        validateLength(request.getContactInfo(), MAX_CONTACT_INFO_LENGTH, "contactInfo is too long");
        validateLength(request.getAvatarUrl(), MAX_AVATAR_URL_LENGTH, "avatarUrl is too long");
        validateTags(request.getInterestTags(), "interestTags");
        validateTags(request.getSkillTags(), "skillTags");
    }

    private void validateLength(String value, int maxLength, String message) {
        if (value != null && value.length() > maxLength) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    message);
        }
    }

    private void validateTags(List<String> tags, String fieldName) {
        if (tags == null) {
            return;
        }
        if (tags.size() > MAX_TAG_COUNT) {
            throw new BusinessException(
                    ErrorCode.COMMON_001_VALIDATION_ERROR,
                    fieldName + " count exceeds limit");
        }
        for (String tag : tags) {
            if (tag != null && tag.length() > MAX_TAG_LENGTH) {
                throw new BusinessException(
                        ErrorCode.COMMON_001_VALIDATION_ERROR,
                        fieldName + " length exceeds limit");
            }
        }
    }

    private String toJson(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(
                    tags == null ? List.of() : tags);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(
                    ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to serialize tags");
        }
    }

    private List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<List<String>>() {});
        } catch (JsonProcessingException ex) {
            return new ArrayList<>();
        }
    }
}
