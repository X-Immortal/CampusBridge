package com.campusbridge.controller;

import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.RewardSettlement;
import com.campusbridge.service.RewardSettlementService;
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
@RequestMapping("/api/v1/settlements")
public class RewardSettlementController {

    private final CurrentUserResolver currentUserResolver;
    private final RewardSettlementService rewardSettlementService;

    public RewardSettlementController(
            CurrentUserResolver currentUserResolver,
            RewardSettlementService rewardSettlementService) {
        this.currentUserResolver = currentUserResolver;
        this.rewardSettlementService = rewardSettlementService;
    }

    @GetMapping("/tasks/{taskId}")
    public Result getTaskSettlement(@PathVariable Long taskId) {
        RewardSettlement settlement = rewardSettlementService.getSettlementByTaskId(taskId);
        return Result.success(settlement);
    }

    @GetMapping("/my")
    public Result getMySettlements(
            HttpServletRequest servletRequest,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Long userId = currentUserResolver.requireCurrentUserId(servletRequest);
        List<RewardSettlement> settlements = rewardSettlementService.getUserSettlements(userId, page, size);
        int total = rewardSettlementService.countUserSettlements(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("settlements", settlements);
        response.put("total", total);
        response.put("page", page);
        response.put("size", size);
        return Result.success(response);
    }
}
