package com.campusbridge.service.impl;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.pojo.CampusVerification;
import com.campusbridge.service.VerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class VerificationServiceImpl implements VerificationService {

    @Autowired
    private CampusVerificationMapper campusVerificationMapper;

    @Override
    public Object submit(Long userId, RequestDtos.CreateVerificationRequest request) {
        if (userId == null || request == null || request.method == null
                || isBlank(request.realName) || isBlank(request.studentNumber) || isBlank(request.school)) {
            return null;
        }

        CampusVerification latest = campusVerificationMapper.selectLatestByUserId(userId);
        if (latest != null && ("PENDING".equals(latest.getStatus()) || "VERIFIED".equals(latest.getStatus()))) {
            return -1L;
        }

        LocalDateTime now = LocalDateTime.now();
        CampusVerification entity = new CampusVerification();
        entity.setUserId(userId);
        entity.setMethod(request.method.name());
        entity.setRealNameCipher(request.realName);
        entity.setStudentNumberCipher(request.studentNumber);
        entity.setStudentNumberHash(sha256(request.studentNumber));
        entity.setSchool(request.school);
        entity.setDepartment(request.department);
        entity.setEvidenceUrl(request.evidenceUrl);
        if ("STUDENT_CARD_IMAGE".equals(entity.getMethod())) {
            entity.setStatus("VERIFIED");
            entity.setReviewedAt(now);
        } else {
            entity.setStatus("PENDING");
        }
        entity.setCreatedAt(now);

        int affected = campusVerificationMapper.insert(entity);
        if (affected <= 0) {
            return -2L;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", entity.getId());
        payload.put("status", entity.getStatus());
        payload.put("userId", entity.getUserId());
        payload.put("method", entity.getMethod());
        payload.put("createdAt", entity.getCreatedAt());
        return payload;
    }

    @Override
    public CampusVerification getMyLatest(Long userId) {
        if (userId == null) {
            return null;
        }
        return campusVerificationMapper.selectLatestByUserId(userId);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String sha256(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
