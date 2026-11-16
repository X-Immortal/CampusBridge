package com.campusbridge.integration;

import com.campusbridge.entity.User;
import com.campusbridge.enums.UserRole;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.NotificationService;
import com.campusbridge.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationFlowIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private AuthenticationManager authenticationManager;
    @MockitoBean
    private UserMapper users;
    @MockitoBean
    private NotificationService notifications;

    @Test
    void loginIssuesJwtAndProtectedEndpointUsesItsIdentity() throws Exception {
        User user = stubUser(1);
        when(users.selectById(7L)).thenReturn(user);
        when(notifications.countUnread(7L)).thenReturn(3L);
        var result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();
        String token = json.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
        mvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + token)
                        .header("X-User-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(3));
        verify(notifications).countUnread(7L);
    }

    @Test
    void authenticationErasesPasswordFromPrincipal() {
        stubUser(1);
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("alice", "pass123"));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(7L);
        assertThat(principal.getPassword()).isNull();
        assertThat(authentication.getCredentials()).isNull();
    }

    @Test
    void wrongPasswordKeepsExistingErrorContract() throws Exception {
        stubUser(1);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void unknownUserKeepsExistingErrorContract() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"pass123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void disabledUserKeepsExistingErrorContract() throws Exception {
        stubUser(0);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pass123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("账号已被禁用"));
    }

    @Test
    void missingPasswordHashRejectsLogin() throws Exception {
        User user = stubUser(1);
        user.setPasswordHash(null);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pass123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002_INVALID_CREDENTIALS"));
    }

    private User stubUser(int enabled) {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setPasswordHash(passwords.encode("pass123"));
        user.setRole(UserRole.USER);
        user.setEnabled(enabled);
        when(users.selectByUsername("alice")).thenReturn(user);
        return user;
    }
}
