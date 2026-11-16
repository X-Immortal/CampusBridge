package com.campusbridge.integration;

import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.common.GlobalExceptionHandler;
import com.campusbridge.controller.AccountController;
import com.campusbridge.controller.AuthController;
import com.campusbridge.controller.PostController;
import com.campusbridge.controller.ReviewController;
import com.campusbridge.controller.TaskApplicationController;
import com.campusbridge.controller.TaskController;
import com.campusbridge.controller.UserController;
import com.campusbridge.controller.VerificationController;
import com.campusbridge.dto.request.CreateTaskRequest;
import com.campusbridge.dto.request.SaveTaskDraftRequest;
import com.campusbridge.dto.request.UpdatePostRequest;
import com.campusbridge.dto.request.UpdateTaskRequest;
import com.campusbridge.service.AuthService;
import com.campusbridge.service.CompletionService;
import com.campusbridge.service.PostService;
import com.campusbridge.service.ReplyService;
import com.campusbridge.service.ReviewService;
import com.campusbridge.service.TaskApplicationService;
import com.campusbridge.service.TaskDetailService;
import com.campusbridge.service.TaskService;
import com.campusbridge.service.UserProfileService;
import com.campusbridge.service.VerificationService;
import com.campusbridge.service.impl.EmailVerificationCodeSender;
import com.campusbridge.utils.AuthContextUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestValidationIntegrationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final TaskService tasks = mock(TaskService.class);
    private final AuthService auth = mock(AuthService.class);
    private final UserProfileService profiles = mock(UserProfileService.class);
    private final PostService posts = mock(PostService.class);
    private final ReviewService reviews = mock(ReviewService.class);
    private final TaskApplicationService applications = mock(TaskApplicationService.class);
    private final VerificationService verifications = mock(VerificationService.class);
    private final CompletionService completions = mock(CompletionService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.requireCurrentUserId(any())).thenReturn(7L);
        EmailVerificationCodeSender sender = mock(EmailVerificationCodeSender.class);
        AuthContextUtil authContext = mock(AuthContextUtil.class);
        when(authContext.getCurrentUserId()).thenReturn(7L);
        mvc = MockMvcBuilders.standaloneSetup(
                        new PostController(currentUser, posts, mock(ReplyService.class)),
                        new ReviewController(currentUser, reviews),
                        new TaskApplicationController(currentUser, applications),
                        new VerificationController(verifications, authContext),
                        new AuthController(auth, sender),
                        new AccountController(currentUser, auth, sender),
                        new TaskController(currentUser, tasks,
                                mock(TaskDetailService.class), completions),
                        new UserController(currentUser, profiles, mock(ReviewService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @MethodSource("invalidPostBodies")
    void invalidBodyNeverReachesBusinessService(String path, String body) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001_VALIDATION_ERROR"));
        verifyNoInteractions(auth, posts, applications, completions, reviews, tasks);
    }

    static Stream<Arguments> invalidPostBodies() {
        return Stream.of(
                Arguments.of("/api/v1/auth/register", ""),
                Arguments.of("/api/v1/auth/register",
                        "{\"username\":\" \",\"password\":\"pass123\",\"phone\":\"13800000001\","
                                + "\"email\":\"student@example.com\",\"code\":\"123456\"}"),
                Arguments.of("/api/v1/auth/register",
                        "{\"username\":\"student\",\"password\":\"pass123\",\"phone\":\"13800000001\","
                                + "\"email\":\"student@example.com\"}"),
                Arguments.of("/api/v1/auth/login", ""),
                Arguments.of("/api/v1/auth/login", "{\"username\":\" \",\"password\":\"pass123\"}"),
                Arguments.of("/api/v1/posts", "{\"category\":\"INQUIRY_HELP\",\"title\":\" \",\"content\":\"text\"}"),
                Arguments.of("/api/v1/tasks/1/applications", "{\"message\":\" \"}"),
                Arguments.of("/api/v1/tasks/1/completion", "{}"),
                Arguments.of("/api/v1/tasks/1/completion/reject", "{\"reason\":\" \"}"),
                Arguments.of("/api/v1/tasks/1/reviews", "{\"revieweeId\":2,\"rating\":0}"),
                Arguments.of("/api/v1/tasks/1/reviews", "{\"revieweeId\":2,\"rating\":6}")
        );
    }

    @Test
    void overlongProfileBioIsRejectedAtTheBoundary() throws Exception {
        mvc.perform(put("/api/v1/users/me/profile").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("bio", "x".repeat(501)))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(profiles);
    }

    @Test
    void cancellationReasonUsesQueryParameterValidation() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/tasks/1").param("reason", "x".repeat(301)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details", hasItem("reason is too long")));
        verifyNoInteractions(tasks);
    }

    @Test
    void cancellationAllowsOmittedReasonForServiceToCheckTaskState() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/tasks/1"))
                .andExpect(status().isNoContent());
        verify(tasks).cancelTask(7L, 1L, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"method\":\"MANUAL_REVIEW\",\"realName\":\"Student\",\"studentNumber\":\"123\",\"school\":\"Campus\"}",
            "{\"method\":\"CAMPUS_EMAIL\",\"realName\":\"Student\",\"studentNumber\":\"123\","
                    + "\"school\":\"Campus\",\"campusEmail\":\"student@example.com\"}"
    })
    void verificationValidationPreservesItsErrorCode(String body) throws Exception {
        mvc.perform(post("/api/v1/verifications").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFY_001_INVALID_INPUT"));
        verifyNoInteractions(verifications);
    }

    @Test
    void emptyVerificationCodePreservesItsErrorCode() throws Exception {
        mvc.perform(post("/api/v1/verifications/email/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFY_001_INVALID_INPUT"));
        verifyNoInteractions(verifications);
    }

    @Test
    void postUpdateRequiresBothTitleAndContent() throws Exception {
        mvc.perform(put("/api/v1/posts/1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Updated title\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(posts);
    }

    @Test
    void completePostUpdateReachesService() throws Exception {
        mvc.perform(put("/api/v1/posts/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\",\"content\":\"Updated content\"}"))
                .andExpect(status().isOk());
        verify(posts).updatePost(eq(7L), eq(1L), any(UpdatePostRequest.class));
    }

    @Test
    void invalidRegistrationReturnsFieldDetailsBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details", hasItem(startsWith("code:"))))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(auth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":\"草稿\",\"deadline\":\"2000-01-01T00:00:00\"}"})
    void incompleteTaskCanBeSavedAsDraftButCannotBePublished(String body) throws Exception {
        mvc.perform(post("/api/v1/tasks/drafts")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        verify(tasks).saveDraft(eq(7L), any(SaveTaskDraftRequest.class));

        mvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(startsWith("category:"))));
        verify(tasks, org.mockito.Mockito.never()).createTask(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"rewardAmount\":0}", "{\"attachmentIds\":[1]}",
            "{\"title\":\"%s\"}"
    })
    void draftStillEnforcesFieldLimits(String body) throws Exception {
        mvc.perform(post("/api/v1/tasks/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted("x".repeat(101))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tasks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"title\":\"Only a title\"}"})
    void repostRejectsMissingOrIncompleteBody(String body) throws Exception {
        mvc.perform(post("/api/v1/tasks/1/resend")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001_VALIDATION_ERROR"));
        verifyNoInteractions(tasks);
    }

    @Test
    void repostPassesTheRevisedFormToTheService() throws Exception {
        String body = updateBody("2099-01-01T00:00:00").replace(
                "\"title\":", "\"category\":\"ERRAND\",\"campus\":\"仙林\",\"location\":\"南门\",\"title\":");
        mvc.perform(post("/api/v1/tasks/1/resend")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        var request = org.mockito.ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(tasks).resendTask(eq(7L), eq(1L), request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().getTitle()).isEqualTo("新标题");
        org.assertj.core.api.Assertions.assertThat(request.getValue().getDeadline().getYear()).isEqualTo(2099);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/tasks", "/api/v1/tasks/drafts", "/api/v1/tasks/1/resend"})
    void taskPublicationRejectsUnsupportedAttachments(String path) throws Exception {
        String body = updateBody("2099-01-01T00:00:00").replace(
                "\"title\":", "\"attachmentIds\":[1],\"category\":\"ERRAND\","
                        + "\"campus\":\"仙林\",\"location\":\"南门\",\"title\":");
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details", hasItem(startsWith("attachmentIds:"))));
        verifyNoInteractions(tasks);
    }

    @Test
    void repostRejectsExpiredDeadline() throws Exception {
        String body = updateBody("2000-01-01T00:00:00").replace(
                "\"title\":", "\"category\":\"ERRAND\",\"campus\":\"仙林\",\"location\":\"南门\",\"title\":");
        mvc.perform(post("/api/v1/tasks/1/resend")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(startsWith("deadline:"))));
        verifyNoInteractions(tasks);
    }

    @Test
    void taskUpdateRequiresCompleteEditableFields() throws Exception {
        mvc.perform(put("/api/v1/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"新标题\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(startsWith("deadline:"))));
        verifyNoInteractions(tasks);
    }

    @Test
    void completeTaskUpdateAcceptsClearedOptionalValues() throws Exception {
        mvc.perform(put("/api/v1/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody("2099-01-01T00:00:00")))
                .andExpect(status().isOk());
        verify(tasks).updateTask(eq(7L), eq(1L), any(UpdateTaskRequest.class));
    }

    @Test
    void completeTaskUpdateRejectsPastDeadline() throws Exception {
        mvc.perform(put("/api/v1/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody("2000-01-01T00:00:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(startsWith("deadline:"))));
        verifyNoInteractions(tasks);
    }

    private String updateBody(String deadline) {
        return """
                {"title":"新标题","description":"新的任务说明","deadline":"%s","rewardType":"FREE",
                 "rewardDescription":null,"rewardAmount":null,"completionStandard":"送达",
                 "requiresProof":false,"onlyInAppContact":true,"allowOfflineContact":false}
                """.formatted(deadline);
    }

    @Test
    void profileChecksTagElementLength() throws Exception {
        mvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("interestTags", List.of("x".repeat(31))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(startsWith("interestTags[0]"))));
        verifyNoInteractions(profiles);
    }

    @Test
    void shortPasswordIsRejectedBeforeAccountService() throws Exception {
        mvc.perform(put("/api/v1/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"before123\",\"newPassword\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("newPassword: 新密码至少6位")));
        verifyNoInteractions(auth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "　", "13800000001"})
    void optionalPhoneRetainsExistingBlankAndValidNumberBehavior(String phone) throws Exception {
        mvc.perform(put("/api/v1/account/phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone", phone))))
                .andExpect(status().isOk());
        verify(auth).updatePhone(7L, phone);
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "　", "\t\n"})
    void whitespaceOnlyCredentialsAreRejected(String username) throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "pass123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(startsWith("username:"))));
        verifyNoInteractions(auth);
    }

    @Test
    void invalidPhoneIsRejected() throws Exception {
        mvc.perform(put("/api/v1/account/phone")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"123\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auth);
    }
}
