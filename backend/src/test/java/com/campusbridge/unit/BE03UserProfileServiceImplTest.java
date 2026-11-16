package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.UpdateUserProfileRequest;
import com.campusbridge.dto.view.UserHomepageView;
import com.campusbridge.dto.view.UserProfileView;
import com.campusbridge.entity.CampusVerification;
import com.campusbridge.entity.User;
import com.campusbridge.entity.UserCredit;
import com.campusbridge.entity.UserProfile;
import com.campusbridge.enums.UserRole;
import com.campusbridge.enums.VerificationStatus;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.mapper.UserCreditMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.mapper.UserProfileMapper;
import com.campusbridge.service.impl.UserProfileServiceImpl;
import com.campusbridge.utils.AliOSSUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BE03UserProfileServiceImplTest {
    private static final Long USER_ID = 1L;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private UserCreditMapper userCreditMapper;

    @Mock
    private CampusVerificationMapper campusVerificationMapper;

    @Mock
    private AliOSSUtils aliOSSUtils;

    private UserProfileServiceImpl userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileServiceImpl(
                userMapper,
                userProfileMapper,
                new ObjectMapper(),
                userCreditMapper,
                campusVerificationMapper,
                aliOSSUtils);
    }

    @Test
    void getMyProfileCreatesEmptyProfileWhenMissing() {
        when(userMapper.selectById(USER_ID)).thenReturn(user());
        when(userProfileMapper.selectByUserId(USER_ID)).thenReturn(null);
        when(userProfileMapper.insert(any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile profile = invocation.getArgument(0);
            profile.setId(100L);
            return 1;
        });

        UserProfileView profile = userProfileService.getMyProfile(USER_ID);

        assertThat(profile.getUserId()).isEqualTo(USER_ID);
        assertThat(profile.getInterestTags()).isEmpty();
        assertThat(profile.getSkillTags()).isEmpty();
    }

    @Test
    void updateMyProfileAllowsNullableFieldsAndSerializesTags() {
        UserProfile existing = profile();
        when(userMapper.selectById(USER_ID)).thenReturn(user());
        when(userProfileMapper.selectByUserId(USER_ID)).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);

        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setNickname("小桥");
        request.setBio(null);
        request.setCampus("鼓楼校区");
        request.setInterestTags(List.of("跑腿", "二手"));
        request.setSkillTags(List.of("Java"));

        UserProfileView updated = userProfileService.updateMyProfile(USER_ID, request);

        assertThat(updated.getNickname()).isEqualTo("小桥");
        assertThat(updated.getBio()).isNull();
        assertThat(updated.getInterestTags()).containsExactly("跑腿", "二手");
        assertThat(updated.getSkillTags()).containsExactly("Java");
    }

    @Test
    void getUserHomepageReturnsPublicSummaryOnly() {
        when(userMapper.selectById(USER_ID)).thenReturn(user());
        when(userProfileMapper.selectByUserId(USER_ID)).thenReturn(profile());
        when(userCreditMapper.selectByUserId(USER_ID)).thenReturn(credit());
        when(campusVerificationMapper.selectLatestByUserId(USER_ID))
                .thenReturn(verifiedRecord());

        UserHomepageView homepage = userProfileService.getUserHomepage(USER_ID);

        assertThat(homepage.getUserId()).isEqualTo(USER_ID);
        assertThat(homepage.getVerified()).isTrue();
        assertThat(homepage.getCreditLevel()).isEqualTo(3);
        assertThat(homepage.getPoints()).isEqualTo(120);
        assertThat(homepage.getInterestTags()).containsExactly("跑腿");
        assertThat(homepage.getSkillTags()).containsExactly("Java");
    }

    @Test
    void getMyProfileRejectsMissingUser() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> userProfileService.getMyProfile(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.USER_001_NOT_FOUND));
    }

    private User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("student");
        user.setRole(UserRole.USER);
        user.setEnabled(1);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private UserProfile profile() {
        UserProfile profile = new UserProfile();
        profile.setId(100L);
        profile.setUserId(USER_ID);
        profile.setNickname("小桥");
        profile.setBio("互助平台用户");
        profile.setCampus("仙林校区");
        profile.setCollege("软件学院");
        profile.setContactInfo("只在个人资料接口可见");
        profile.setAvatarUrl("https://example.com/avatar.png");
        profile.setInterestTags("[\"跑腿\"]");
        profile.setSkillTags("[\"Java\"]");
        return profile;
    }

    private UserCredit credit() {
        UserCredit credit = new UserCredit();
        credit.setUserId(USER_ID);
        credit.setPoints(120);
        credit.setLevel(3);
        return credit;
    }

    private CampusVerification verifiedRecord() {
        CampusVerification verification = new CampusVerification();
        verification.setUserId(USER_ID);
        verification.setStatus(VerificationStatus.VERIFIED);
        return verification;
    }
}
