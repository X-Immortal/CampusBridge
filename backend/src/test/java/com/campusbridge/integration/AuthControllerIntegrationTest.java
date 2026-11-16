package com.campusbridge.integration;

import com.campusbridge.dto.request.RegisterRequest;
import com.campusbridge.entity.User;
import com.campusbridge.enums.UserRole;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.AuthService;
import com.campusbridge.service.PostService;
import com.campusbridge.security.JwtTokenIssuer;
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
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private JwtTokenIssuer tokens;

    @Autowired
    private JwtEncoder encoder;

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
    void protectedEndpointRejectsMissingJwt() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Missing or invalid JWT token"));
    }
    @Test
    void refreshTokenCannotAuthenticateProtectedRequest() throws Exception {
        mockMvc.perform(put("/api/v1/account/password")
                        .header("Authorization", "Bearer " + tokens.issueRefreshToken(100L))
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
                        .header("Authorization", "Bearer " + tokens.issueAccessToken(100L, UserRole.USER)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingAccountCannotUseAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.issueAccessToken(100L, UserRole.USER)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void previousAdminTokenCannotBypassCurrentRole() throws Exception {
        stubUser("USER", 1);
        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header("Authorization", "Bearer " + tokens.issueAccessToken(100L, UserRole.ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MOD_001_PERMISSION_DENIED"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void refreshTokenOnPublicEndpointIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/posts/10")
                        .header("Authorization", "Bearer " + tokens.issueRefreshToken(100L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001_UNAUTHORIZED"));
        verifyNoInteractions(postService);
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
                        .header("Authorization", "Bearer " + tokens.issueAccessToken(100L, UserRole.USER)))
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
        user.setRole(UserRole.valueOf(role));
        user.setEnabled(enabled);
        when(userMapper.selectById(100L)).thenReturn(user);
    }

    private String legacyToken(String role) {
        var claims = JwtClaimsSet.builder().subject("100").claim("accountId", 100L)
                .expiresAt(Instant.now().plusSeconds(60));
        if (role != null) {
            claims.claim("role", role);
        }
        return encode(encoder, claims.build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "expired", "not-yet-valid", "wrong-signature",
            "mismatched-account", "missing-expiry", "unknown-type"})
    void invalidTokensAreRejectedByResourceServer(String scenario) throws Exception {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().subject("100").claim("accountId", 100L)
                .claim("tokenType", "ACCESS").issuedAt(now.minusSeconds(600))
                .expiresAt(now.plusSeconds(300));
        JwtEncoder signingEncoder = encoder;
        switch (scenario) {
            case "expired" -> claims.expiresAt(now.minusSeconds(300));
            case "not-yet-valid" -> claims.notBefore(now.plusSeconds(120));
            case "wrong-signature" -> signingEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                    new SecretKeySpec("different-test-secret-at-least-32-bytes"
                            .getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
            case "mismatched-account" -> claims.subject("101");
            case "missing-expiry" -> claims.claims(values -> values.remove("exp"));
            case "unknown-type" -> claims.claim("tokenType", "UNKNOWN");
            default -> { }
        }
        String token = scenario.equals("malformed") ? "not-a-jwt" : encode(signingEncoder, claims.build());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001_UNAUTHORIZED"));
        verifyNoInteractions(userMapper);
    }

    private String encode(JwtEncoder signingEncoder, JwtClaimsSet claims) {
        return signingEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
