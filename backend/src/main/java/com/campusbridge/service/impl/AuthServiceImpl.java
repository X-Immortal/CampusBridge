package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.LoginRequest;
import com.campusbridge.dto.request.RegisterRequest;
import com.campusbridge.dto.view.LoginView;
import com.campusbridge.entity.User;
import com.campusbridge.enums.UserRole;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.AuthService;
import com.campusbridge.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int EMAIL_CODE_TTL_MINUTES = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final ConcurrentHashMap<Long, AccountEmailRecord> accountEmailStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AccountEmailRecord> registrationCodeStore = new ConcurrentHashMap<>();
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationCodeSender emailCodeSender;

    @Value("${campusbridge.verification.email.code-secret:campusbridge-email-code}")
    private String emailCodeSecret;

    @Override
    public Long register(RegisterRequest request) {

        if (userMapper.selectByUsername(request.username) != null) {
            throw new BusinessException(ErrorCode.COMMON_003_CONFLICT, "用户名已存在");
        }

        String phoneHash = sha256(request.phone);
        if (userMapper.selectByPhoneHash(phoneHash) != null) {
            throw new BusinessException(ErrorCode.COMMON_003_CONFLICT, "手机号已存在");
        }

        String email = normalizeEmail(request.email);
        AccountEmailRecord record = registrationCodeStore.get(email);
        requireValidCode(record, request.code);
        if (!registrationCodeStore.remove(email, record)) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "验证码已使用或已更新，请重新发送");
        }

        User user = new User();
        user.setUsername(request.username);
        user.setPasswordHash(passwordEncoder.encode(request.password));
        user.setPhoneCipher(request.phone);
        user.setPhoneHash(phoneHash);
        user.setEmail(email);
        user.setRole(UserRole.USER);
        user.setEnabled(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        int affected = userMapper.insert(user);
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "注册保存失败");
        }
        return user.getId();
    }

    @Override
    public String sendRegistrationCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EMAIL_CODE_TTL_MINUTES);
        boolean sent = emailCodeSender.sendCode(normalizedEmail, code, expiresAt);
        if (!sent && !emailCodeSender.isDebugCodeEnabled()) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "验证码发送失败，请检查邮箱配置");
        }
        registrationCodeStore.put(normalizedEmail, new AccountEmailRecord(
                normalizedEmail, expiresAt,
                sha256(emailCodeSecret + ":" + normalizedEmail + ":" + code)));
        return code;
    }

    @Override
    public LoginView login(LoginRequest request) {

        User dbUser = userMapper.selectByUsername(request.username);
        if (dbUser == null) {
            throw new BusinessException(ErrorCode.AUTH_002_INVALID_CREDENTIALS);
        }
        if (dbUser.getEnabled() != null && dbUser.getEnabled() == 0) {
            throw new BusinessException(ErrorCode.AUTH_001_UNAUTHORIZED, "账号已被禁用");
        }
        if (dbUser.getPasswordHash() == null
                || !passwordEncoder.matches(request.password, dbUser.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_002_INVALID_CREDENTIALS);
        }

        return new LoginView(dbUser.getId(), dbUser.getUsername(), dbUser.getRole(),
                jwtUtil.generateToken(dbUser.getId(), dbUser.getRole()),
                jwtUtil.generateRefreshToken(dbUser.getId()));
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = requireUser(userId);
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_002_INVALID_CREDENTIALS, "旧密码不正确");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword), LocalDateTime.now());
    }

    @Override
    public String sendAccountEmailCode(Long userId, String email) {
        requireUser(userId);
        email = normalizeEmail(email);
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EMAIL_CODE_TTL_MINUTES);
        boolean sent = emailCodeSender.sendCode(email, code, expiresAt);
        if (!sent && !emailCodeSender.isDebugCodeEnabled()) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "验证码发送失败，请检查邮箱配置");
        }
        accountEmailStore.put(userId, new AccountEmailRecord(
                email, expiresAt,
                sha256(emailCodeSecret + ":" + normalizeEmail(email) + ":" + code)));
        return code;
    }

    @Override
    public void confirmAccountEmailChange(Long userId, String code) {
        AccountEmailRecord record = accountEmailStore.get(userId);
        requireValidCode(record, code);
        if (!accountEmailStore.remove(userId, record)) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "验证码已使用或已更新，请重新发送");
        }
        requireUser(userId);
        userMapper.updateEmail(userId, record.email, LocalDateTime.now());
    }

    @Override
    public void updatePhone(Long userId, String phone) {
        requireUser(userId);
        String phoneHash = sha256(phone);
        if (phone != null && !phone.isBlank()) {
            User existing = userMapper.selectByPhoneHash(phoneHash);
            if (existing != null && !existing.getId().equals(userId)) {
                throw new BusinessException(ErrorCode.COMMON_003_CONFLICT, "该手机号已被其他账号使用");
            }
        }
        userMapper.updatePhone(userId, phone, phoneHash, LocalDateTime.now());
    }

    private void requireValidCode(AccountEmailRecord record, String code) {
        if (record == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "请先发送邮箱验证码");
        }
        if (!record.expiresAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "验证码已过期，请重新发送");
        }
        String expectedHash = sha256(emailCodeSecret + ":" + record.email + ":" + code.trim());
        if (!expectedHash.equals(record.codeHash)) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "验证码不正确");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_001_UNAUTHORIZED, "用户不存在");
        }
        return user;
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

    private record AccountEmailRecord(
            String email, LocalDateTime expiresAt, String codeHash) {
    }
}
