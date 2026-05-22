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
                LOGGER.info("OCR result unavailable for verificationId={}", verificationId);
                return;
            }

            boolean matched = isMatched(entity, ocrResult);
            if (matched) {
                entity.setStatus("VERIFIED");
                entity.setRejectReason(null);
            } else {
                entity.setStatus("REJECTED");
                entity.setRejectReason("OCR extracted fields do not match submitted data");
            }
            entity.setReviewedAt(LocalDateTime.now());
            campusVerificationMapper.updateById(entity);
        } catch (Exception e) {
            LOGGER.warn("Auto review failed, verificationId={}", verificationId, e);
        }
    }

    private boolean isMatched(CampusVerification entity, VerificationOcrClient.OcrExtractResult ocrResult) {
        return normalizedEquals(entity.getRealNameCipher(), ocrResult.getRealName())
                && normalizedEquals(entity.getStudentNumberCipher(), ocrResult.getStudentNumber())
                && normalizedContainsEither(entity.getSchool(), ocrResult.getSchool());
    }

    private boolean normalizedEquals(String left, String right) {
        return normalize(left).equals(normalize(right));
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
