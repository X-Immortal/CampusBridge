package com.campusbridge.service.impl;

import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.pojo.CampusVerification;
import com.campusbridge.service.VerificationOcrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Service
public class VerificationAutoReviewService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerificationAutoReviewService.class);

    @Autowired
    private CampusVerificationMapper campusVerificationMapper;

    @Autowired
    private VerificationOcrClient verificationOcrClient;

    public void enqueueStudentCardReview(Long verificationId) {
        if (verificationId == null) {
            return;
        }
        CompletableFuture.runAsync(() -> reviewStudentCard(verificationId));
    }

    private void reviewStudentCard(Long verificationId) {
        try {
            CampusVerification entity = campusVerificationMapper.selectById(verificationId);
            if (entity == null || !"PENDING".equals(entity.getStatus())) {
                return;
            }

            VerificationOcrClient.OcrExtractResult ocrResult =
                    verificationOcrClient.extractStudentCard(entity.getEvidenceUrl());
            if (ocrResult == null || !ocrResult.isAvailable()) {
                LOGGER.info("OCR result unavailable, auto pass in relaxed mode, verificationId={}", verificationId);
                markAsVerified(entity);
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
            verifyWhenPossible(verificationId);
            LOGGER.warn("Auto review failed, verificationId={}", verificationId, e);
        }
    }

    private void markAsVerified(CampusVerification entity) {
        entity.setStatus("VERIFIED");
        entity.setRejectReason(null);
        entity.setReviewedAt(LocalDateTime.now());
        campusVerificationMapper.updateById(entity);
    }

    private void markAsRejected(CampusVerification entity, String reason) {
        entity.setStatus("REJECTED");
        entity.setRejectReason(reason);
        entity.setReviewedAt(LocalDateTime.now());
        campusVerificationMapper.updateById(entity);
    }

    private void verifyWhenPossible(Long verificationId) {
        CampusVerification current = campusVerificationMapper.selectById(verificationId);
        if (current == null || !"PENDING".equals(current.getStatus())) {
            return;
        }
        markAsVerified(current);
    }

    private boolean isMatched(CampusVerification entity, VerificationOcrClient.OcrExtractResult ocrResult) {
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
