package com.campusbridge.controller;

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
            return Result.error("AUTH_004_INVALID_INPUT", "注册参数不合法");
        }
        if (userId == -1L) {
            return Result.error("AUTH_005_USERNAME_EXISTS", "用户名已存在");
        }
        if (userId == -2L) {
            return Result.error("AUTH_006_PHONE_EXISTS", "手机号已存在");
        }
        return Result.success(userId);
    }

    @PostMapping("/login")
    public Result login(@RequestBody RequestDtos.LoginRequest request) {
        Object loginResult = authService.login(request);
        if (loginResult == null) {
            return Result.error("AUTH_004_INVALID_INPUT", "登录参数不合法");
        }
        if (loginResult instanceof Long code) {
            if (code == -1L || code == -3L) {
                return Result.error("AUTH_002_INVALID_CREDENTIALS", "用户名或密码错误");
            }
            if (code == -2L) {
                return Result.error("AUTH_001_UNAUTHORIZED", "账号已被禁用");
            }
        }
        return Result.success(loginResult);
    }
}
