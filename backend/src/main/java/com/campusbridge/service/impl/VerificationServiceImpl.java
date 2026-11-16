package com.campusbridge.service.impl;

import com.campusbridge.enums.EmailDeliveryStatus;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.request.ConfirmEmailVerificationRequest;
import com.campusbridge.dto.request.CreateVerificationRequest;
import com.campusbridge.dto.view.VerificationCancelledView;
import com.campusbridge.dto.view.VerificationReviewView;
import com.campusbridge.dto.view.VerificationSubmissionView;
import com.campusbridge.entity.CampusVerification;
import com.campusbridge.enums.VerificationMethod;
import com.campusbridge.enums.VerificationStatus;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

    private static final String EMAIL_EVIDENCE_PREFIX = "EMAIL_CODE";
    private static final int EMAIL_CODE_TTL_MINUTES = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[^@\\s]+@[^@\\s]*\\.edu\\.cn$", Pattern.CASE_INSENSITIVE);

    private final CampusVerificationMapper campusVerificationMapper;
    private final EmailVerificationCodeSender emailVerificationCodeSender;

    @Value("${campusbridge.verification.email.code-secret:campusbridge-email-code}")
    private String emailCodeSecret;

    @Override
    public VerificationSubmissionView submit(Long userId, CreateVerificationRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VERIFY_001_INVALID_INPUT);
        }

        CampusVerification latest = campusVerificationMapper.selectLatestByUserId(userId);
        if (latest != null && latest.getStatus() == VerificationStatus.VERIFIED) {
            throw new BusinessException(ErrorCode.VERIFY_002_ALREADY_PENDING_OR_VERIFIED);
        }
        if (latest != null && latest.getStatus() == VerificationStatus.PENDING) {
            if (request.method == VerificationMethod.CAMPUS_EMAIL
                    && latest.getMethod() == VerificationMethod.CAMPUS_EMAIL) {
                applyRequestToEntity(latest, request);
                return persistEmailCode(latest, request.campusEmail, false);
            }
            throw new BusinessException(ErrorCode.VERIFY_002_ALREADY_PENDING_OR_VERIFIED);
        }

        LocalDateTime now = LocalDateTime.now();
        CampusVerification entity = new CampusVerification();
        entity.setUserId(userId);
        entity.setCreatedAt(now);
        applyRequestToEntity(entity, request);

        if (request.method == VerificationMethod.CAMPUS_EMAIL) {
            return persistEmailCode(entity, request.campusEmail, true);
        }

        int affected = campusVerificationMapper.insert(entity);
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.VERIFY_003_SUBMIT_FAILED);
        }

        return buildPayload(entity);
    }

    @Override
    public VerificationSubmissionView confirmEmail(Long userId, ConfirmEmailVerificationRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VERIFY_001_INVALID_INPUT);
        }

        CampusVerification latest = campusVerificationMapper.selectLatestByUserId(userId);
        if (latest == null) {
            throw new BusinessException(ErrorCode.VERIFY_004_NOT_FOUND);
        }
        if (latest.getStatus() != VerificationStatus.PENDING
                || latest.getMethod() != VerificationMethod.CAMPUS_EMAIL) {
            throw new BusinessException(ErrorCode.VERIFY_006_EMAIL_CODE_NOT_PENDING);
        }

        EmailCodeRecord record = parseEmailCodeRecord(latest.getEvidenceUrl());
        if (record == null) {
            throw new BusinessException(ErrorCode.VERIFY_006_EMAIL_CODE_NOT_PENDING);
        }
        if (record.expiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.VERIFY_007_EMAIL_CODE_EXPIRED);
        }
        if (!hashEmailCode(record.email(), request.code.trim()).equals(record.codeHash())) {
            throw new BusinessException(ErrorCode.VERIFY_008_EMAIL_CODE_MISMATCH);
        }

        latest.setStatus(VerificationStatus.VERIFIED);
        latest.setReviewerId(null);
        latest.setRejectReason(null);
        latest.setEvidenceUrl(record.email());
        latest.setReviewedAt(LocalDateTime.now());

        int affected = campusVerificationMapper.updatePending(latest);
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.COMMON_003_CONFLICT);
        }
        return buildPayload(latest);
    }

    @Override
    public VerificationSubmissionView resendEmailCode(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VERIFY_001_INVALID_INPUT);
        }
        CampusVerification latest = campusVerificationMapper.selectLatestByUserId(userId);
        if (latest == null) {
            throw new BusinessException(ErrorCode.VERIFY_004_NOT_FOUND);
        }
        if (latest.getStatus() != VerificationStatus.PENDING
                || latest.getMethod() != VerificationMethod.CAMPUS_EMAIL) {
            throw new BusinessException(ErrorCode.VERIFY_006_EMAIL_CODE_NOT_PENDING);
        }

        EmailCodeRecord record = parseEmailCodeRecord(latest.getEvidenceUrl());
        if (record == null || !isValidEmail(record.email())) {
            throw new BusinessException(ErrorCode.VERIFY_006_EMAIL_CODE_NOT_PENDING);
        }
        return persistEmailCode(latest, record.email(), false);
    }

    @Override
    public VerificationCancelledView cancelLatestPending(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VERIFY_001_INVALID_INPUT);
        }

        CampusVerification latest = campusVerificationMapper.selectLatestByUserId(userId);
        if (latest == null) {
            throw new BusinessException(ErrorCode.VERIFY_004_NOT_FOUND);
        }
        if (latest.getStatus() != VerificationStatus.PENDING) {
            throw new BusinessException(ErrorCode.VERIFY_009_CANNOT_CANCEL);
        }

        int affected = campusVerificationMapper.deletePendingById(latest.getId());
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.COMMON_003_CONFLICT);
        }

        return new VerificationCancelledView(true, latest.getId());
    }

    @Override
    public CampusVerification getMyLatest(Long userId) {
        if (userId == null) {
            return null;
        }
        CampusVerification verification = campusVerificationMapper.selectLatestByUserId(userId);
        hideEmailChallenge(verification);
        return verification;
    }

    @Override
    public PagedResult<CampusVerification> listPending(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<CampusVerification> result = campusVerificationMapper.selectPage(
                new Page<>(safePage, safeSize),
                Wrappers.<CampusVerification>lambdaQuery()
                        .eq(CampusVerification::getStatus, VerificationStatus.PENDING)
                        .orderByDesc(CampusVerification::getCreatedAt));
        List<CampusVerification> list = result.getRecords();
        long total = result.getTotal();
        list.forEach(this::hideEmailChallenge);
        return new PagedResult<>(list, safePage, safeSize, total);
    }

    @Override
    public VerificationReviewView review(Long adminUserId, Long verificationId, boolean approved, String rejectReason) {
        if (adminUserId == null || verificationId == null) {
            throw new BusinessException(ErrorCode.VERIFY_001_INVALID_INPUT);
        }

        CampusVerification entity = campusVerificationMapper.selectById(verificationId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.VERIFY_004_NOT_FOUND);
        }
        if (entity.getStatus() != VerificationStatus.PENDING) {
            throw new BusinessException(ErrorCode.COMMON_003_CONFLICT);
        }

        entity.setReviewerId(adminUserId);
        entity.setReviewedAt(LocalDateTime.now());
        if (approved) {
            entity.setStatus(VerificationStatus.VERIFIED);
            entity.setRejectReason(null);
        } else {
            entity.setStatus(VerificationStatus.REJECTED);
            entity.setRejectReason(isBlank(rejectReason) ? "管理员拒绝" : rejectReason);
        }

        int affected = campusVerificationMapper.updatePending(entity);
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.COMMON_003_CONFLICT);
        }

        return new VerificationReviewView(entity.getId(), entity.getStatus(),
                entity.getReviewerId(), entity.getReviewedAt());
    }

    private void applyRequestToEntity(
            CampusVerification entity,
            CreateVerificationRequest request) {
        entity.setMethod(request.method);
        entity.setRealNameCipher(request.realName);
        entity.setStudentNumberCipher(request.studentNumber);
        entity.setStudentNumberHash(sha256(request.studentNumber));
        entity.setSchool(request.school);
        entity.setDepartment(request.department);
        entity.setEvidenceUrl(request.evidenceUrl);
        entity.setStatus(VerificationStatus.PENDING);
        entity.setRejectReason(null);
        entity.setReviewerId(null);
        entity.setReviewedAt(null);
    }

    private VerificationSubmissionView persistEmailCode(CampusVerification entity, String rawEmail, boolean insert) {
        String email = normalizeEmail(rawEmail);
        String code = generateEmailCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EMAIL_CODE_TTL_MINUTES);
        boolean sent = emailVerificationCodeSender.sendCode(email, code, expiresAt);
        if (!sent && (emailVerificationCodeSender.isDeliveryRequired()
                || !emailVerificationCodeSender.isDebugCodeEnabled())) {
            throw new BusinessException(ErrorCode.VERIFY_005_EMAIL_SEND_FAILED);
        }

        entity.setEvidenceUrl(buildEmailCodeEvidence(email, code, expiresAt));
        int affected = insert
                ? campusVerificationMapper.insert(entity)
                : campusVerificationMapper.updatePending(entity);
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.VERIFY_003_SUBMIT_FAILED);
        }

        return new VerificationSubmissionView(entity.getId(), entity.getStatus(), entity.getUserId(),
                entity.getMethod(), entity.getCreatedAt(), entity.getReviewedAt(), email,
                sent ? EmailDeliveryStatus.SENT : EmailDeliveryStatus.DEV_FALLBACK,
                !sent && emailVerificationCodeSender.isDebugCodeEnabled() ? code : null);
    }

    private void hideEmailChallenge(CampusVerification verification) {
        if (verification != null && verification.getMethod() == VerificationMethod.CAMPUS_EMAIL) {
            EmailCodeRecord record = parseEmailCodeRecord(verification.getEvidenceUrl());
            if (record != null) {
                verification.setEvidenceUrl(record.email());
            }
        }
    }

    private VerificationSubmissionView buildPayload(CampusVerification entity) {
        return new VerificationSubmissionView(entity.getId(), entity.getStatus(), entity.getUserId(),
                entity.getMethod(), entity.getCreatedAt(), entity.getReviewedAt(), null, null, null);
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
