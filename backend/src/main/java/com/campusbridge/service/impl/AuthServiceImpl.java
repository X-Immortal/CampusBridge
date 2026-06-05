package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.pojo.User;
import com.campusbridge.service.AuthService;
import com.campusbridge.service.impl.EmailVerificationCodeSender;
import com.campusbridge.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int EMAIL_CODE_TTL_MINUTES = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final ConcurrentHashMap<Long, AccountEmailRecord> accountEmailStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AccountEmailRecord> registrationCodeStore = new ConcurrentHashMap<>();

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationCodeSender emailCodeSender;

    @Value("${campusbridge.verification.email.code-secret:campusbridge-email-code}")
    private String emailCodeSecret;

    @Override
    public Long register(RequestDtos.RegisterRequest request) {
        if (request == null || request.username == null || request.username.isBlank()
                || request.password == null || request.password.isBlank()) {
            return null;
        }

        if (userMapper.selectByUsername(request.username) != null) {
            return -1L;
        }

        if (request.email != null && !request.email.isBlank()) {
            if (!registrationCodeStore.containsKey(request.email.toLowerCase().trim())) {
                throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "请先发送邮箱验证码");
            }
            AccountEmailRecord record = registrationCodeStore.get(request.email.toLowerCase().trim());
            if (record.expiresAt.isBefore(LocalDateTime.now())) {
                registrationCodeStore.remove(request.email.toLowerCase().trim());
                throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "验证码已过期，请重新发送");
            }
            String expectedHash = sha256(
                    emailCodeSecret + ":" + request.email.toLowerCase().trim() + ":" + request.code.trim());
            if (!expectedHash.equals(record.codeHash)) {
                throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "验证码不正确");
            }
            registrationCodeStore.remove(request.email.toLowerCase().trim());
        }

        String phoneHash = sha256(request.phone);
        if (phoneHash != null && userMapper.selectByPhoneHash(phoneHash) != null) {
            return -2L;
        }

        User user = new User();
        user.setUsername(request.username);
        user.setPasswordHash(passwordEncoder.encode(request.password));
        user.setPhoneCipher(request.phone);
        user.setPhoneHash(phoneHash);
        user.setEmail(request.email);
        user.setRole("USER");
        user.setEnabled(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        int affected = userMapper.insert(user);
        if (affected <= 0) {
            return null;
        }
        return user.getId();
    }

    @Override
    public String sendRegistrationCode(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "邮箱不能为空");
        }
        String normalizedEmail = email.toLowerCase().trim();
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EMAIL_CODE_TTL_MINUTES);
        boolean sent = emailCodeSender.sendCode(normalizedEmail, code, expiresAt);
        if (!sent && emailCodeSender.isDeliveryRequired()) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "验证码发送失败，请检查邮箱配置");
        }
        registrationCodeStore.put(normalizedEmail, new AccountEmailRecord(
                normalizedEmail, code, expiresAt,
                sha256(emailCodeSecret + ":" + normalizedEmail + ":" + code)));
        return code;
    }

    @Override
    public Object login(RequestDtos.LoginRequest request) {
        if (request == null || request.username == null || request.username.isBlank()
                || request.password == null || request.password.isBlank()) {
            return null;
        }

        User dbUser = userMapper.selectByUsername(request.username);
        if (dbUser == null) {
            return -1L;
        }
        if (dbUser.getEnabled() != null && dbUser.getEnabled() == 0) {
            return -2L;
        }
        if (dbUser.getPasswordHash() == null
                || !passwordEncoder.matches(request.password, dbUser.getPasswordHash())) {
            return -3L;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", dbUser.getId());
        payload.put("username", dbUser.getUsername());
        payload.put("role", dbUser.getRole());
        payload.put("token", jwtUtil.generateToken(dbUser.getId(), dbUser.getRole()));
        payload.put("refreshToken", jwtUtil.generateRefreshToken(dbUser.getId()));
        return payload;
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = requireUser(userId);
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_002_INVALID_CREDENTIALS, "旧密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public String sendAccountEmailCode(Long userId, String email) {
        requireUser(userId);
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EMAIL_CODE_TTL_MINUTES);
        boolean sent = emailCodeSender.sendCode(email, code, expiresAt);
        if (!sent && emailCodeSender.isDeliveryRequired()) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "验证码发送失败，请检查邮箱配置");
        }
        accountEmailStore.put(userId, new AccountEmailRecord(
                email, code, expiresAt,
                sha256(emailCodeSecret + ":" + email.toLowerCase().trim() + ":" + code)));
        return code;
    }

    @Override
    public void confirmAccountEmailChange(Long userId, String code) {
        AccountEmailRecord record = accountEmailStore.get(userId);
        if (record == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "未发送验证码或已过期，请重新发送");
        }
        if (record.expiresAt.isBefore(LocalDateTime.now())) {
            accountEmailStore.remove(userId);
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "验证码已过期，请重新发送");
        }
        String expectedHash = sha256(
                emailCodeSecret + ":" + record.email.toLowerCase().trim() + ":" + code.trim());
        if (!expectedHash.equals(record.codeHash)) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "验证码不正确");
        }
        User user = requireUser(userId);
        user.setEmail(record.email);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        accountEmailStore.remove(userId);
    }

    @Override
    public void updatePhone(Long userId, String phone) {
        User user = requireUser(userId);
        String phoneHash = sha256(phone);
        if (phone != null && !phone.isBlank()) {
            User existing = userMapper.selectByPhoneHash(phoneHash);
            if (existing != null && !existing.getId().equals(userId)) {
                throw new BusinessException(ErrorCode.COMMON_003_CONFLICT, "该手机号已被其他账号使用");
            }
        }
        user.setPhoneCipher(phone);
        user.setPhoneHash(phoneHash);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
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
            String email, String code, LocalDateTime expiresAt, String codeHash) {
    }
}
