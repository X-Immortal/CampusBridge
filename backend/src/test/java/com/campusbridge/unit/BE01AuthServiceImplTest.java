package com.campusbridge.unit;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.pojo.User;
import com.campusbridge.service.impl.AuthServiceImpl;
import com.campusbridge.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BE01AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    // ---- register ----

    @Test
    void registerCreatesUserAndReturnsId() {
        RequestDtos.RegisterRequest request = registerRequest("alice", "pass123", "13800000001", null);
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
    void registerReturnsNullForNullRequest() {
        assertThat(authService.register(null)).isNull();
    }

    @Test
    void registerReturnsNullForBlankUsername() {
        RequestDtos.RegisterRequest request = registerRequest("  ", "pass123", "13800000001", null);
        assertThat(authService.register(request)).isNull();
    }

    @Test
    void registerReturnsMinusOneForDuplicateUsername() {
        RequestDtos.RegisterRequest request = registerRequest("alice", "pass123", "13800000001", null);
        when(userMapper.selectByUsername("alice")).thenReturn(new User());

        assertThat(authService.register(request)).isEqualTo(-1L);
    }

    @Test
    void registerReturnsMinusTwoForDuplicatePhone() {
        RequestDtos.RegisterRequest request = registerRequest("alice", "pass123", "13800000001", null);
        when(userMapper.selectByUsername("alice")).thenReturn(null);
        when(userMapper.selectByPhoneHash(any())).thenReturn(new User());

        assertThat(authService.register(request)).isEqualTo(-2L);
    }

    @Test
    void registerReturnsNullWhenInsertFails() {
        RequestDtos.RegisterRequest request = registerRequest("alice", "pass123", "13800000001", null);
        when(userMapper.selectByUsername("alice")).thenReturn(null);
        when(userMapper.selectByPhoneHash(any())).thenReturn(null);
        when(passwordEncoder.encode("pass123")).thenReturn("hashed-pass");
        when(userMapper.insert(any(User.class))).thenReturn(0);

        assertThat(authService.register(request)).isNull();
    }

    // ---- login ----

    @Test
    void loginReturnsPayloadForValidCredentials() {
        RequestDtos.LoginRequest request = loginRequest("alice", "pass123");
        User dbUser = user(100L, "alice", "hashed-pass", "USER");
        when(userMapper.selectByUsername("alice")).thenReturn(dbUser);
        when(passwordEncoder.matches("pass123", "hashed-pass")).thenReturn(true);
        when(jwtUtil.generateToken(100L, "USER")).thenReturn("jwt-token");
        when(jwtUtil.generateRefreshToken(100L)).thenReturn("refresh-token");

        Object result = authService.login(request);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result;
        assertThat(payload.get("userId")).isEqualTo(100L);
        assertThat(payload.get("username")).isEqualTo("alice");
        assertThat(payload.get("role")).isEqualTo("USER");
        assertThat(payload.get("token")).isEqualTo("jwt-token");
        assertThat(payload.get("refreshToken")).isEqualTo("refresh-token");
    }

    @Test
    void loginReturnsNullForNullRequest() {
        assertThat(authService.login(null)).isNull();
    }

    @Test
    void loginReturnsNullForBlankUsername() {
        RequestDtos.LoginRequest request = loginRequest("  ", "pass123");
        assertThat(authService.login(request)).isNull();
    }

    @Test
    void loginReturnsMinusOneForUnknownUser() {
        RequestDtos.LoginRequest request = loginRequest("nobody", "pass123");
        when(userMapper.selectByUsername("nobody")).thenReturn(null);

        assertThat(authService.login(request)).isEqualTo(-1L);
    }

    @Test
    void loginReturnsMinusTwoForDisabledUser() {
        RequestDtos.LoginRequest request = loginRequest("alice", "pass123");
        User dbUser = user(100L, "alice", "hashed-pass", "USER");
        dbUser.setEnabled(0);
        when(userMapper.selectByUsername("alice")).thenReturn(dbUser);

        assertThat(authService.login(request)).isEqualTo(-2L);
    }

    @Test
    void loginReturnsMinusThreeForWrongPassword() {
        RequestDtos.LoginRequest request = loginRequest("alice", "wrong");
        User dbUser = user(100L, "alice", "hashed-pass", "USER");
        when(userMapper.selectByUsername("alice")).thenReturn(dbUser);
        when(passwordEncoder.matches("wrong", "hashed-pass")).thenReturn(false);

        assertThat(authService.login(request)).isEqualTo(-3L);
    }

    @Test
    void loginReturnsMinusThreeForNullPasswordHash() {
        RequestDtos.LoginRequest request = loginRequest("alice", "pass123");
        User dbUser = user(100L, "alice", null, "USER");
        when(userMapper.selectByUsername("alice")).thenReturn(dbUser);

        assertThat(authService.login(request)).isEqualTo(-3L);
    }

    // ---- helpers ----

    private RequestDtos.RegisterRequest registerRequest(String username, String password, String phone, String email) {
        RequestDtos.RegisterRequest request = new RequestDtos.RegisterRequest();
        request.username = username;
        request.password = password;
        request.phone = phone;
        request.email = email;
        return request;
    }

    private RequestDtos.LoginRequest loginRequest(String username, String password) {
        RequestDtos.LoginRequest request = new RequestDtos.LoginRequest();
        request.username = username;
        request.password = password;
        return request;
    }

    private User user(Long id, String username, String passwordHash, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setEnabled(1);
        return user;
    }
}