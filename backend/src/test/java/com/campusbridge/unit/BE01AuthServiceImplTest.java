package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.RegisterRequest;
import com.campusbridge.entity.User;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.impl.AuthServiceImpl;
import com.campusbridge.service.impl.EmailVerificationCodeSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BE01AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailVerificationCodeSender emailCodeSender;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userMapper, passwordEncoder, emailCodeSender);
        ReflectionTestUtils.setField(authService, "emailCodeSecret", "test-secret");
    }

    // ---- register ----

    @Test
    void registerCreatesUserAndReturnsId() {
        when(emailCodeSender.sendCode(any(), any(), any())).thenReturn(true);
        String code = authService.sendRegistrationCode("alice@test.com");

        RegisterRequest request =
                registerRequest("alice", "pass123", "13800000001", "alice@test.com", code);
        when(userMapper.selectByUsername("alice")).thenReturn(null);
        when(userMapper.selectByPhoneHash(any())).thenReturn(null);
        when(passwordEncoder.encode("pass123")).thenReturn("hashed-pass");
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(100L);
            return 1;
        });

        Long result = authService.register(request);

        assertThat(result).isEqualTo(100L);
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        RegisterRequest request =
                registerRequest("alice", "pass123", "13800000001", "alice@test.com", "000000");
        when(userMapper.selectByUsername("alice")).thenReturn(new User());

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMMON_003_CONFLICT);
    }

    @Test
    void registerRejectsDuplicatePhone() {
        when(emailCodeSender.sendCode(any(), any(), any())).thenReturn(true);
        String code = authService.sendRegistrationCode("alice@test.com");

        RegisterRequest request =
                registerRequest("alice", "pass123", "13800000001", "alice@test.com", code);
        when(userMapper.selectByUsername("alice")).thenReturn(null);
        when(userMapper.selectByPhoneHash(any())).thenReturn(new User());

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMMON_003_CONFLICT);
    }

    @Test
    void registerRejectsFailedInsert() {
        when(emailCodeSender.sendCode(any(), any(), any())).thenReturn(true);
        String code = authService.sendRegistrationCode("alice@test.com");

        RegisterRequest request =
                registerRequest("alice", "pass123", "13800000001", "alice@test.com", code);
        when(userMapper.selectByUsername("alice")).thenReturn(null);
        when(userMapper.selectByPhoneHash(any())).thenReturn(null);
        when(passwordEncoder.encode("pass123")).thenReturn("hashed-pass");
        when(userMapper.insert(any(User.class))).thenReturn(0);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMMON_999_INTERNAL_ERROR);
    }

    @Test
    void registrationCodeIsConsumedOnlyOnceAcrossConcurrentRequests() throws Exception {
        when(emailCodeSender.sendCode(any(), any(), any())).thenReturn(true);
        String code = authService.sendRegistrationCode("alice@test.com");
        CountDownLatch bothCheckedPhone = new CountDownLatch(2);
        when(userMapper.selectByPhoneHash(any())).thenAnswer(invocation -> {
            bothCheckedPhone.countDown();
            assertThat(bothCheckedPhone.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        });
        when(passwordEncoder.encode("pass123")).thenReturn("hash");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(100L);
            return 1;
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(executor.submit(() -> {
                    try {
                        return authService.register(
                                registerRequest("alice", "pass123", "13800000001", "alice@test.com", code));
                    } catch (BusinessException ex) {
                        return ex;
                    }
                }));
            }
            List<Object> outcomes = List.of(results.get(0).get(5, TimeUnit.SECONDS),
                    results.get(1).get(5, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(Long.class::isInstance).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);
            verify(userMapper, times(1)).insert(any(User.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void registrationFailsWhenMailIsUnavailableAndDebugModeIsDisabled() {
        assertThatThrownBy(() -> authService.sendRegistrationCode("alice@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码发送失败");
    }

    @Test
    void registrationAllowsExplicitDebugModeWithoutMailDelivery() {
        when(emailCodeSender.isDebugCodeEnabled()).thenReturn(true);
        assertThat(authService.sendRegistrationCode("alice@test.com")).matches("[0-9]{6}");
    }

    // ---- helpers ----

    private RegisterRequest registerRequest(
            String username, String password, String phone, String email, String code) {
        RegisterRequest request = new RegisterRequest();
        request.username = username;
        request.password = password;
        request.phone = phone;
        request.email = email;
        request.code = code;
        return request;
    }

}
