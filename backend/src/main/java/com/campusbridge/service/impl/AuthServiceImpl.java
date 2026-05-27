package com.campusbridge.service.impl;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.pojo.User;
import com.campusbridge.service.AuthService;
import com.campusbridge.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public Long register(RequestDtos.RegisterRequest request) {
        if (request == null || request.username == null || request.username.isBlank()
                || request.password == null || request.password.isBlank()) {
            return null;
        }

        if (userMapper.selectByUsername(request.username) != null) {
            return -1L;
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
