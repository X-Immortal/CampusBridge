package com.campusbridge.controller;

import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.pojo.CreditRecord;
import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.UserCredit;
import com.campusbridge.service.UserCreditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/credits")
public class UserCreditController {

    private final CurrentUserResolver currentUserResolver;
    private final UserCreditService userCreditService;

    public UserCreditController(CurrentUserResolver currentUserResolver, UserCreditService userCreditService) {
        this.currentUserResolver = currentUserResolver;
        this.userCreditService = userCreditService;
    }

    /**
     * 获取当前用户的信用信息
     */
    @GetMapping("/me")
    public Result getMyCredit(HttpServletRequest servletRequest) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        UserCredit userCredit = userCreditService.getUserCredit(currentUserId);
        return Result.success(userCredit);
    }

    /**
     * 获取指定用户的信用信息
     */
    @GetMapping("/users/{userId}")
    public Result getUserCredit(@PathVariable Long userId) {
        UserCredit userCredit = userCreditService.getUserCredit(userId);
        return Result.success(userCredit);
    }

    /**
     * 获取当前用户的信用分变化记录
     */
    @GetMapping("/me/records")
    public Result getMyCreditRecords(
            HttpServletRequest servletRequest,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Long currentUserId = currentUserResolver.requireCurrentUserId(servletRequest);
        List<CreditRecord> records = userCreditService.getCreditRecords(currentUserId, page, size);
        
        Map<String, Object> response = new HashMap<>();
        response.put("records", records);
        response.put("page", page);
        response.put("size", size);
        return Result.success(response);
    }

    /**
     * 获取指定用户的信用分变化记录
     */
    @GetMapping("/users/{userId}/records")
    public Result getUserCreditRecords(
            @PathVariable Long userId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        List<CreditRecord> records = userCreditService.getCreditRecords(userId, page, size);
        
        Map<String, Object> response = new HashMap<>();
        response.put("records", records);
        response.put("page", page);
        response.put("size", size);
        return Result.success(response);
    }
}
