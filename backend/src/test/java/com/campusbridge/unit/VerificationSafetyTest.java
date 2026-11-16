package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.ConfirmEmailVerificationRequest;
import com.campusbridge.dto.request.CreateVerificationRequest;
import com.campusbridge.dto.response.OcrExtractResult;
import com.campusbridge.dto.view.VerificationSubmissionView;
import com.campusbridge.entity.CampusVerification;
import com.campusbridge.enums.VerificationMethod;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.service.VerificationOcrClient;
import com.campusbridge.service.impl.EmailVerificationCodeSender;
import com.campusbridge.service.impl.VerificationAutoReviewService;
import com.campusbridge.service.impl.VerificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        autoReview = new VerificationAutoReviewService(mapper, ocr);
        verification = new VerificationServiceImpl(mapper, sender);
        ReflectionTestUtils.setField(verification, "emailCodeSecret", "test-only-secret");
    }

    @Test
    void unavailableOcrLeavesVerificationPending() {
        CampusVerification pending = pending();
        when(mapper.selectById(1L)).thenReturn(pending);
        when(ocr.extractStudentCard(pending.getEvidenceUrl()))
                .thenReturn(OcrExtractResult.unavailable());

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
                OcrExtractResult.of("Student", "12345678", "Campus"));
        ReflectionTestUtils.invokeMethod(autoReview, "reviewStudentCard", 1L);
        assertThat(pending.getStatus()).isEqualTo("VERIFIED");
        verify(mapper).updatePending(pending);
    }

    @Test
    void disabledEmailWithoutExplicitDebugModeCannotCreateChallenge() {
        assertThatThrownBy(() -> verification.submit(7L, emailRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VERIFY_005_EMAIL_SEND_FAILED);
        verify(mapper, never()).insert(any(CampusVerification.class));
    }

    @Test
    void explicitDebugModeReturnsCodeButReadDoesNotExposeChallengeHash() {
        when(sender.isDebugCodeEnabled()).thenReturn(true);
        when(mapper.insert(any(CampusVerification.class))).thenAnswer(invocation -> {
            CampusVerification entity = invocation.getArgument(0);
            entity.setId(1L);
            when(mapper.selectLatestByUserId(7L)).thenReturn(entity);
            return 1;
        });
        VerificationSubmissionView result = verification.submit(7L, emailRequest());
        assertThat(result.debugCode()).matches("\\d{6}");
        assertThat(verification.getMyLatest(7L).getEvidenceUrl()).isEqualTo("student@campus.edu.cn");
    }

    @Test
    void reviewLostRaceReturnsFailureInsteadOfReportingApproval() {
        when(mapper.selectById(1L)).thenReturn(pending());
        when(mapper.updatePending(any())).thenReturn(0);
        assertThatThrownBy(() -> verification.review(99L, 1L, true, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMMON_003_CONFLICT);
    }

    @Test
    void resendSaveFailureIsNotReportedAsMissingPendingVerification() {
        when(sender.isDebugCodeEnabled()).thenReturn(true);
        when(mapper.insert(any(CampusVerification.class))).thenAnswer(call -> {
            CampusVerification entity = call.getArgument(0);
            entity.setId(1L);
            when(mapper.selectLatestByUserId(7L)).thenReturn(entity);
            return 1;
        });
        verification.submit(7L, emailRequest());
        when(mapper.updatePending(any())).thenReturn(0);
        assertThatThrownBy(() -> verification.resendEmailCode(7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VERIFY_003_SUBMIT_FAILED);
    }

    @Test
    void incorrectEmailCodeCannotCompleteVerification() {
        when(sender.isDebugCodeEnabled()).thenReturn(true);
        when(mapper.insert(any(CampusVerification.class))).thenAnswer(call -> {
            CampusVerification entity = call.getArgument(0);
            entity.setId(1L);
            when(mapper.selectLatestByUserId(7L)).thenReturn(entity);
            return 1;
        });
        var submitted = verification.submit(7L, emailRequest());
        ConfirmEmailVerificationRequest request = new ConfirmEmailVerificationRequest();
        request.setCode(submitted.debugCode().equals("000000") ? "111111" : "000000");
        assertThatThrownBy(() -> verification.confirmEmail(7L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VERIFY_008_EMAIL_CODE_MISMATCH);
        verify(mapper, never()).updatePending(any());
    }

    private CreateVerificationRequest emailRequest() {
        CreateVerificationRequest request = new CreateVerificationRequest();
        request.setMethod(VerificationMethod.CAMPUS_EMAIL);
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
