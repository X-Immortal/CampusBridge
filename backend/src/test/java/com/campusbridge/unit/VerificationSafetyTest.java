package com.campusbridge.unit;

import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.pojo.CampusVerification;
import com.campusbridge.service.VerificationOcrClient;
import com.campusbridge.service.impl.EmailVerificationCodeSender;
import com.campusbridge.service.impl.VerificationAutoReviewService;
import com.campusbridge.service.impl.VerificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationSafetyTest {
    private CampusVerificationMapper mapper;
    private VerificationOcrClient ocr;
    private VerificationAutoReviewService autoReview;
    private VerificationServiceImpl verification;
    private EmailVerificationCodeSender sender;

    @BeforeEach
    void setUp() {
        mapper = mock(CampusVerificationMapper.class);
        ocr = mock(VerificationOcrClient.class);
        sender = mock(EmailVerificationCodeSender.class);
        autoReview = new VerificationAutoReviewService();
        ReflectionTestUtils.setField(autoReview, "campusVerificationMapper", mapper);
        ReflectionTestUtils.setField(autoReview, "verificationOcrClient", ocr);
        verification = new VerificationServiceImpl();
        ReflectionTestUtils.setField(verification, "campusVerificationMapper", mapper);
        ReflectionTestUtils.setField(verification, "emailVerificationCodeSender", sender);
        ReflectionTestUtils.setField(verification, "emailCodeSecret", "test-only-secret");
    }

    @Test
    void unavailableOcrLeavesVerificationPending() {
        CampusVerification pending = pending();
        when(mapper.selectById(1L)).thenReturn(pending);
        when(ocr.extractStudentCard(pending.getEvidenceUrl()))
                .thenReturn(VerificationOcrClient.OcrExtractResult.unavailable());

        ReflectionTestUtils.invokeMethod(autoReview, "reviewStudentCard", 1L);

        assertThat(pending.getStatus()).isEqualTo("PENDING");
        verify(mapper, never()).updatePending(any());
    }

    @Test
    void failedOcrDoesNotApproveVerification() {
        when(mapper.selectById(1L)).thenReturn(pending());
        when(ocr.extractStudentCard(any())).thenThrow(new IllegalStateException("OCR unavailable"));

        ReflectionTestUtils.invokeMethod(autoReview, "reviewStudentCard", 1L);

        verify(mapper, never()).updatePending(any());
    }

    @Test
    void matchingOcrUsesConditionalReviewUpdate() {
        CampusVerification pending = pending();
        when(mapper.selectById(1L)).thenReturn(pending);
        when(ocr.extractStudentCard(any())).thenReturn(
                VerificationOcrClient.OcrExtractResult.of("Student", "12345678", "Campus"));
        ReflectionTestUtils.invokeMethod(autoReview, "reviewStudentCard", 1L);
        assertThat(pending.getStatus()).isEqualTo("VERIFIED");
        verify(mapper).updatePending(pending);
    }

    @Test
    void disabledEmailWithoutExplicitDebugModeCannotCreateChallenge() {
        assertThat(verification.submit(7L, emailRequest())).isEqualTo(-4L);
        verify(mapper, never()).insert(any());
    }

    @Test
    void explicitDebugModeReturnsCodeButReadDoesNotExposeChallengeHash() {
        when(sender.isDebugCodeEnabled()).thenReturn(true);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            CampusVerification entity = invocation.getArgument(0);
            entity.setId(1L);
            when(mapper.selectLatestByUserId(7L)).thenReturn(entity);
            return 1;
        });
        Object result = verification.submit(7L, emailRequest());
        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("debugCode")).asString().matches("\\d{6}");
        assertThat(verification.getMyLatest(7L).getEvidenceUrl()).isEqualTo("student@campus.edu.cn");
    }

    @Test
    void reviewLostRaceReturnsFailureInsteadOfReportingApproval() {
        when(mapper.selectById(1L)).thenReturn(pending());
        when(mapper.updatePending(any())).thenReturn(0);
        assertThat(verification.review(99L, 1L, true, null)).isEqualTo(-2L);
    }

    private RequestDtos.CreateVerificationRequest emailRequest() {
        RequestDtos.CreateVerificationRequest request = new RequestDtos.CreateVerificationRequest();
        request.setMethod(ApiEnums.VerificationMethod.CAMPUS_EMAIL);
        request.setCampusEmail("student@campus.edu.cn");
        request.setRealName("Student");
        request.setStudentNumber("12345678");
        request.setSchool("Campus");
        return request;
    }

    private CampusVerification pending() {
        CampusVerification entity = new CampusVerification();
        entity.setId(1L);
        entity.setUserId(7L);
        entity.setMethod("STUDENT_CARD_IMAGE");
        entity.setStatus("PENDING");
        entity.setEvidenceUrl("https://example.test/student.png");
        entity.setRealNameCipher("Student");
        entity.setStudentNumberCipher("12345678");
        entity.setSchool("Campus");
        return entity;
    }
}
