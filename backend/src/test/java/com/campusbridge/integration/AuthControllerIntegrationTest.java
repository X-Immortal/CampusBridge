package com.campusbridge.integration;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.LoginRequest;
import com.campusbridge.dto.request.RegisterRequest;
import com.campusbridge.dto.view.LoginView;
import com.campusbridge.entity.User;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.AuthService;
import com.campusbridge.service.PostService;
import com.campusbridge.utils.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "security.jwt.required=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private PostService postService;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void registerReturnsUnifiedCreatedResponse() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(100L);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "Campus@12345",
                                  "phone": "13800000001",
                                  "email": "alice@example.edu.cn",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data").value(100))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void loginInvalidCredentialsReturnsBusinessError() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new BusinessException(ErrorCode.AUTH_002_INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void loginReturnsServicePayloadWhenCredentialsAreValid() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(new LoginView(100L, "alice", "USER", "jwt-token", "refresh-token"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "Campus@12345"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.userId").value(100))
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }

    @Test
    void protectedEndpointRejectsMissingJwt() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Missing or invalid JWT token"));
    }
    @Test
    void refreshTokenCannotAuthenticateProtectedRequest() throws Exception {
        mockMvc.perform(put("/api/v1/account/password")
                        .header("Authorization", "Bearer " + jwtUtil.generateRefreshToken(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"before123\",\"newPassword\":\"after123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void legacyRefreshTokenCannotAuthenticateProtectedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + legacyToken(null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyAccessTokenUsesAuthenticatedIdentity() throws Exception {
        stubUser("USER", 1);
        mockMvc.perform(put("/api/v1/account/password")
                        .header("Authorization", "Bearer " + legacyToken("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"before123\",\"newPassword\":\"after123\"}"))
                .andExpect(status().isOk());
        verify(authService).changePassword(100L, "before123", "after123");
    }

    @Test
    void disabledAccountCannotUsePreviouslyIssuedAccessToken() throws Exception {
        stubUser("USER", 0);
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(100L, "USER")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingAccountCannotUseAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(100L, "USER")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void previousAdminTokenCannotBypassCurrentRole() throws Exception {
        stubUser("USER", 1);
        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(100L, "ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MOD_001_PERMISSION_DENIED"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void refreshTokenOnPublicEndpointRemainsAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/posts/10")
                        .header("Authorization", "Bearer " + jwtUtil.generateRefreshToken(100L)))
                .andExpect(status().isOk());
        verify(postService).getPostDetail(eq(10L), isNull());
    }

    @Test
    void malformedPathParameterReturnsValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/users/not-a-number/profile"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001_VALIDATION_ERROR"));
    }

    @Test
    void missingUploadPartReturnsValidationError() throws Exception {
        stubUser("USER", 1);
        mockMvc.perform(multipart("/api/v1/attachments")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(100L, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001_VALIDATION_ERROR"));
    }

    @Test
    void concurrentRegistrationDuplicateReturnsConflictWithoutDatabaseDetails() throws Exception {
        when(authService.register(any())).thenThrow(new DuplicateKeyException("private database detail"));
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"username":"alice","password":"pass123","phone":"13800000001",
                                 "email":"alice@example.edu.cn","code":"123456"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_003_CONFLICT"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    private void stubUser(String role, int enabled) {
        User user = new User();
        user.setId(100L);
        user.setRole(role);
        user.setEnabled(enabled);
        when(userMapper.selectById(100L)).thenReturn(user);
    }

    private String legacyToken(String role) {
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("accountId", 100L);
        if (role != null) {
            claims.put("role", role);
        }
        return Jwts.builder().setClaims(claims).setSubject("100")
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(SignatureAlgorithm.HS256, Keys.hmacShaKeyFor(
                        "test-secret-key-at-least-64-characters-for-hs256-please-do-not-use-prod"
                                .getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
