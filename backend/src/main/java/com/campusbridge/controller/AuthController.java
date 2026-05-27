package com.campusbridge.controller;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Result;
import com.campusbridge.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result register(@RequestBody RequestDtos.RegisterRequest request) {
        Long userId = authService.register(request);
        if (userId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "注册参数不合法");
        }
        if (userId == -1L) {
            throw new BusinessException(ErrorCode.COMMON_003_CONFLICT, "用户名已存在");
        }
        if (userId == -2L) {
            throw new BusinessException(ErrorCode.COMMON_003_CONFLICT, "手机号已存在");
        }
        return Result.success(userId);
    }

    @PostMapping("/login")
    public Result login(@RequestBody RequestDtos.LoginRequest request) {
        Object loginResult = authService.login(request);
        if (loginResult == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "登录参数不合法");
        }
        if (loginResult instanceof Long code) {
            if (code == -1L || code == -3L) {
                throw new BusinessException(ErrorCode.AUTH_002_INVALID_CREDENTIALS);
            }
            if (code == -2L) {
                throw new BusinessException(ErrorCode.AUTH_001_UNAUTHORIZED, "账号已被禁用");
            }
        }
        return Result.success(loginResult);
    }
}
