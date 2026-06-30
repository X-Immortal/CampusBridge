package com.campusbridge.service.impl;

import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.pojo.CampusVerification;
import com.campusbridge.service.VerificationService;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class VerificationServiceImpl implements VerificationService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_VERIFIED = "VERIFIED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String EMAIL_EVIDENCE_PREFIX = "EMAIL_CODE";
    private static final int EMAIL_CODE_TTL_MINUTES = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[^@\\s]+@[^@\\s]*\\.edu\\.cn$", Pattern.CASE_INSENSITIVE);

    @Autowired
    private CampusVerificationMapper campusVerificationMapper;

    @Autowired
    private EmailVerificationCodeSender emailVerificationCodeSender;

    @Value("${campusbridge.verification.email.code-secret:campusbridge-email-code}")
    private String emailCodeSecret;

    @Override
    public Object submit(Long userId, RequestDtos.CreateVerificationRequest request) {
        if (userId == null || request == null || request.method == null
                || isBlank(request.realName) || isBlank(request.studentNumber) || isBlank(request.school)) {
            return null;
        }
        if (requiresEvidenceUrl(request.method) && isBlank(request.evidenceUrl)) {
            return null;
        }
        if (request.method == ApiEnums.VerificationMethod.CAMPUS_EMAIL
                && !isValidEmail(request.campusEmail)) {
            return null;
        }

        CampusVerification latest = campusVerificationMapper.selectLatestByUserId(userId);
        if (latest != null && STATUS_VERIFIED.equals(latest.getStatus())) {
            return -1L;
        }
        if (latest != null && STATUS_PENDING.equals(latest.getStatus())) {
            if (request.method == ApiEnums.VerificationMethod.CAMPUS_EMAIL
                    && ApiEnums.VerificationMethod.CAMPUS_EMAIL.name().equals(latest.getMethod())) {
                applyRequestToEntity(latest, request);
                return persistEmailCode(latest, request.campusEmail, false);
            }
            return -1L;
        }

        LocalDateTime now = LocalDateTime.now();
        CampusVerification entity = new CampusVerification();
        entity.setUserId(userId);
        entity.setCreatedAt(now);
        applyRequestToEntity(entity, request);

        if (request.method == ApiEnums.VerificationMethod.CAMPUS_EMAIL) {
            return persistEmailCode(entity, request.campusEmail, true);
        }

        int affected = campusVerificationMapper.insert(entity);
        if (affected <= 0) {
            return -2L;
        }

        return buildPayload(entity);
    }

    @Override
    public Object confirmEmail(Long userId, RequestDtos.ConfirmEmailVerificationRequest request) {
        if (userId == null || request == null || isBlank(request.code)) {
            return null;
        }

        CampusVerification latest = campusVerificationMapper.selectLatestByUserId(userId);
        if (latest == null) {
            return -1L;
        }
        if (!STATUS_PENDING.equals(latest.getStatus())
                || !ApiEnums.VerificationMethod.CAMPUS_EMAIL.name().equals(latest.getMethod())) {
            return -2L;
        }

        EmailCodeRecord record = parseEmailCodeRecord(latest.getEvidenceUrl());
        if (record == null) {
            return -3L;
        }
        if (record.expiresAt().isBefore(LocalDateTime.now())) {
            return -4L;
        }
        if (!hashEmailCode(record.email(), request.code.trim()).equals(record.codeHash())) {
            return -5L;
        }

        latest.setStatus(STATUS_VERIFIED);
        latest.setReviewerId(null);
        latest.setRejectReason(null);
        latest.setEvidenceUrl(record.email());
        latest.setReviewedAt(LocalDateTime.now());

        int affected = campusVerificationMapper.updateById(latest);
        if (affected <= 0) {
            return -6L;
        }
        return buildPayload(latest);
    }

    @Override
    public Object resendEmailCode(Long userId) {
        if (userId == null) {
            return null;
        }
        CampusVerification latest = campusVerificationMapper.selectLatestByUserId(userId);
        if (latest == null) {
            return -1L;
        }
        if (!STATUS_PENDING.equals(latest.getStatus())
                || !ApiEnums.VerificationMethod.CAMPUS_EMAIL.name().equals(latest.getMethod())) {
            return -2L;
        }

        EmailCodeRecord record = parseEmailCodeRecord(latest.getEvidenceUrl());
        if (record == null || !isValidEmail(record.email())) {
            return -3L;
        }
        return persistEmailCode(latest, record.email(), false);
    }

    @Override
    public Object cancelLatestPending(Long userId) {
        if (userId == null) {
            return null;
        }

        CampusVerification latest = campusVerificationMapper.selectLatestByUserId(userId);
        if (latest == null) {
            return -1L;
        }
        if (!STATUS_PENDING.equals(latest.getStatus())) {
            return -2L;
        }

        int affected = campusVerificationMapper.deleteById(latest.getId());
        if (affected <= 0) {
            return -3L;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("cancelled", true);
        payload.put("id", latest.getId());
        return payload;
    }

    @Override
    public CampusVerification getMyLatest(Long userId) {
        if (userId == null) {
            return null;
        }
        return campusVerificationMapper.selectLatestByUserId(userId);
    }

    @Override
    public ResponseDtos.PagedResult<CampusVerification> listPending(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageHelper.startPage(safePage, safeSize);
        List<CampusVerification> list = campusVerificationMapper.selectByStatus(STATUS_PENDING);
        long total = list instanceof com.github.pagehelper.Page<?> pageResult
                ? pageResult.getTotal()
                : list.size();
        return new ResponseDtos.PagedResult<>(list, safePage, safeSize, total);
    }

    @Override
    public Object review(Long adminUserId, Long verificationId, boolean approved, String rejectReason) {
        if (adminUserId == null || verificationId == null) {
            return null;
        }

        CampusVerification entity = campusVerificationMapper.selectById(verificationId);
        if (entity == null) {
            return -1L;
        }
        if (!STATUS_PENDING.equals(entity.getStatus())) {
            return -2L;
        }

        entity.setReviewerId(adminUserId);
        entity.setReviewedAt(LocalDateTime.now());
        if (approved) {
            entity.setStatus(STATUS_VERIFIED);
            entity.setRejectReason(null);
        } else {
            entity.setStatus(STATUS_REJECTED);
            entity.setRejectReason(isBlank(rejectReason) ? "管理员拒绝" : rejectReason);
        }

        int affected = campusVerificationMapper.updateById(entity);
        if (affected <= 0) {
            return -3L;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", entity.getId());
        payload.put("status", entity.getStatus());
        payload.put("reviewerId", entity.getReviewerId());
        payload.put("reviewedAt", entity.getReviewedAt());
        return payload;
    }

    private void applyRequestToEntity(
            CampusVerification entity,
            RequestDtos.CreateVerificationRequest request) {
        entity.setMethod(request.method.name());
        entity.setRealNameCipher(request.realName);
        entity.setStudentNumberCipher(request.studentNumber);
        entity.setStudentNumberHash(sha256(request.studentNumber));
        entity.setSchool(request.school);
        entity.setDepartment(request.department);
        entity.setEvidenceUrl(request.evidenceUrl);
        entity.setStatus(STATUS_PENDING);
        entity.setRejectReason(null);
        entity.setReviewerId(null);
        entity.setReviewedAt(null);
    }

    private Object persistEmailCode(CampusVerification entity, String rawEmail, boolean insert) {
        String email = normalizeEmail(rawEmail);
        String code = generateEmailCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EMAIL_CODE_TTL_MINUTES);
        boolean sent = emailVerificationCodeSender.sendCode(email, code, expiresAt);
        if (!sent && emailVerificationCodeSender.isDeliveryRequired()) {
            return -4L;
        }

        entity.setEvidenceUrl(buildEmailCodeEvidence(email, code, expiresAt));
        int affected = insert
                ? campusVerificationMapper.insert(entity)
                : campusVerificationMapper.updateById(entity);
        if (affected <= 0) {
            return -2L;
        }

        Map<String, Object> payload = buildPayload(entity);
        payload.put("campusEmail", email);
        payload.put("emailDelivery", sent ? "SENT" : "DEV_FALLBACK");
        if (!sent) {
            payload.put("debugCode", code);
        }
        return payload;
    }

    private Map<String, Object> buildPayload(CampusVerification entity) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", entity.getId());
        payload.put("status", entity.getStatus());
        payload.put("userId", entity.getUserId());
        payload.put("method", entity.getMethod());
        payload.put("createdAt", entity.getCreatedAt());
        payload.put("reviewedAt", entity.getReviewedAt());
        return payload;
    }

    private boolean requiresEvidenceUrl(ApiEnums.VerificationMethod method) {
        return method == ApiEnums.VerificationMethod.STUDENT_CARD_IMAGE
                || method == ApiEnums.VerificationMethod.MANUAL_REVIEW;
    }

    private boolean isValidEmail(String value) {
        String email = normalizeEmail(value);
        return !isBlank(email) && EMAIL_PATTERN.matcher(email).matches();
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String generateEmailCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String buildEmailCodeEvidence(String email, String code, LocalDateTime expiresAt) {
        return EMAIL_EVIDENCE_PREFIX
                + "|" + email
                + "|" + expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "|" + hashEmailCode(email, code);
    }

    private EmailCodeRecord parseEmailCodeRecord(String evidenceUrl) {
        if (isBlank(evidenceUrl) || !evidenceUrl.startsWith(EMAIL_EVIDENCE_PREFIX + "|")) {
            return null;
        }
        String[] parts = evidenceUrl.split("\\|", 4);
        if (parts.length != 4 || !isValidEmail(parts[1])) {
            return null;
        }
        try {
            return new EmailCodeRecord(
                    parts[1],
                    LocalDateTime.parse(parts[2], DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    parts[3]);
        } catch (Exception e) {
            return null;
        }
    }

    private String hashEmailCode(String email, String code) {
        return sha256(emailCodeSecret + ":" + normalizeEmail(email) + ":" + code);
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

    private record EmailCodeRecord(String email, LocalDateTime expiresAt, String codeHash) {
    }
}
