package com.campusbridge.integration;

import com.campusbridge.common.GlobalExceptionHandler;
import com.campusbridge.controller.AttachmentController;
import com.campusbridge.controller.VerificationController;
import com.campusbridge.entity.Attachment;
import com.campusbridge.entity.CampusVerification;
import com.campusbridge.enums.VerificationStatus;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.service.impl.AttachmentServiceImpl;
import com.campusbridge.service.impl.EmailVerificationCodeSender;
import com.campusbridge.service.impl.VerificationServiceImpl;
import com.campusbridge.utils.AliOSSUtils;
import com.campusbridge.entity.User;
import com.campusbridge.enums.UserRole;
import com.campusbridge.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TypedResponseErrorIntegrationTest {
    private static final String EMAIL_REQUEST = """
            {"method":"CAMPUS_EMAIL","realName":"Student","studentNumber":"12345678",
             "school":"Campus","campusEmail":"student@campus.edu.cn"}
            """;
    private CampusVerificationMapper verifications;
    private AttachmentMapper attachments;
    private EmailVerificationCodeSender sender;
    private AliOSSUtils oss;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        verifications = mock(CampusVerificationMapper.class);
        attachments = mock(AttachmentMapper.class);
        sender = mock(EmailVerificationCodeSender.class);
        oss = mock(AliOSSUtils.class);
        User user = new User();
        user.setId(7L);
        user.setRole(UserRole.USER);
        user.setEnabled(1);
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));

        VerificationServiceImpl verificationService = new VerificationServiceImpl(verifications, sender);
        ReflectionTestUtils.setField(verificationService, "emailCodeSecret", "test-only-secret");
        VerificationController verificationController = new VerificationController(verificationService);

        AttachmentServiceImpl attachmentService = new AttachmentServiceImpl(attachments, oss);
        AttachmentController attachmentController = new AttachmentController(attachmentService);
        mvc = MockMvcBuilders.standaloneSetup(verificationController, attachmentController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void invalidVerificationReturns400InsteadOfAccepted() throws Exception {
        mvc.perform(post("/api/v1/verifications").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFY_001_INVALID_INPUT"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void repeatedVerificationReturnsConflict() throws Exception {
        CampusVerification existing = new CampusVerification();
        existing.setStatus(VerificationStatus.VERIFIED);
        when(verifications.selectLatestByUserId(7L)).thenReturn(existing);
        mvc.perform(post("/api/v1/verifications").contentType(MediaType.APPLICATION_JSON).content(EMAIL_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERIFY_002_ALREADY_PENDING_OR_VERIFIED"));
    }

    @Test
    void absentVerificationReturns404() throws Exception {
        mvc.perform(get("/api/v1/verifications"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VERIFY_004_NOT_FOUND"));
    }

    @Test
    void emailFailureReturns502InsteadOfAccepted() throws Exception {
        when(sender.isDeliveryRequired()).thenReturn(true);
        mvc.perform(post("/api/v1/verifications").contentType(MediaType.APPLICATION_JSON).content(EMAIL_REQUEST))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("VERIFY_005_EMAIL_SEND_FAILED"));
    }

    @Test
    void verificationSuccessPreservesOptionalFieldVisibility() throws Exception {
        when(sender.sendCode(any(), any(), any())).thenReturn(true);
        when(verifications.insert(any(CampusVerification.class))).thenAnswer(call -> {
            CampusVerification entity = call.getArgument(0);
            entity.setId(10L);
            return 1;
        });
        mvc.perform(post("/api/v1/verifications").contentType(MediaType.APPLICATION_JSON).content(EMAIL_REQUEST))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.campusEmail").value("student@campus.edu.cn"))
                .andExpect(jsonPath("$.data.emailDelivery").value("SENT"))
                .andExpect(jsonPath("$.data.debugCode").doesNotExist())
                .andExpect(jsonPath("$.data.evidenceUrl").doesNotExist());
    }

    @Test
    void nonImageUploadReturns400InsteadOfCreated() throws Exception {
        mvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "proof.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_002_UNSUPPORTED_TYPE"));
    }

    @Test
    void metadataSaveFailureIsNotMisreportedAsUploadFailure() throws Exception {
        when(oss.upload(any())).thenReturn("https://example.test/card.png");
        when(attachments.insert(any(com.campusbridge.entity.Attachment.class))).thenReturn(0);
        mvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "card.png", "image/png", new byte[]{1})))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ATTACH_005_SAVE_FAILED"));
    }
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

}
