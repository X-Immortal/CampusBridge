package com.campusbridge.service.impl;

import com.campusbridge.dto.response.OcrExtractResult;
import com.campusbridge.entity.CampusVerification;
import com.campusbridge.enums.VerificationStatus;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.service.VerificationOcrClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationAutoReviewService {

    private final CampusVerificationMapper campusVerificationMapper;
    private final VerificationOcrClient verificationOcrClient;

    public void enqueueStudentCardReview(Long verificationId) {
        if (verificationId == null) {
            return;
        }
        CompletableFuture.runAsync(() -> reviewStudentCard(verificationId));
    }

    private void reviewStudentCard(Long verificationId) {
        try {
            CampusVerification entity = campusVerificationMapper.selectById(verificationId);
            if (entity == null || entity.getStatus() != VerificationStatus.PENDING) {
                return;
            }

            OcrExtractResult ocrResult =
                    verificationOcrClient.extractStudentCard(entity.getEvidenceUrl());
            if (ocrResult == null || !ocrResult.isAvailable()) {
                log.info("OCR unavailable; verification remains pending, verificationId={}", verificationId);
                return;
            }

            boolean matched = isMatched(entity, ocrResult);
            if (matched) {
                markAsVerified(entity);
                return;
            } else {
                markAsRejected(entity, "OCR extracted fields do not match submitted data");
                return;
            }
        } catch (Exception e) {
            log.warn("Auto review failed, verificationId={}", verificationId, e);
        }
    }

    private void markAsVerified(CampusVerification entity) {
        entity.setStatus(VerificationStatus.VERIFIED);
        entity.setRejectReason(null);
        entity.setReviewedAt(LocalDateTime.now());
        campusVerificationMapper.updatePending(entity);
    }

    private void markAsRejected(CampusVerification entity, String reason) {
        entity.setStatus(VerificationStatus.REJECTED);
        entity.setRejectReason(reason);
        entity.setReviewedAt(LocalDateTime.now());
        campusVerificationMapper.updatePending(entity);
    }

    private boolean isMatched(CampusVerification entity, OcrExtractResult ocrResult) {
        String submittedName = entity.getRealNameCipher();
        String submittedNumber = entity.getStudentNumberCipher();
        String submittedSchool = entity.getSchool();
        String ocrName = ocrResult.getRealName();
        String ocrNumber = ocrResult.getStudentNumber();
        String ocrSchool = ocrResult.getSchool();

        boolean numberExactMatch = normalizedEquals(submittedNumber, ocrNumber);
        boolean numberLooseMatch = normalizedContainsEither(submittedNumber, ocrNumber);
        boolean nameLooseMatch = normalizedContainsEither(submittedName, ocrName);
        boolean schoolLooseMatch = normalizedContainsEither(submittedSchool, ocrSchool);

        // Relaxed rule for coursework:
        // pass if student number is exact/loosely matched, or name+school both loosely matched.
        return numberExactMatch || numberLooseMatch || (nameLooseMatch && schoolLooseMatch);
    }

    private boolean normalizedEquals(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.equals(b);
    }

    private boolean normalizedContainsEither(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.contains(b) || b.contains(a);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
