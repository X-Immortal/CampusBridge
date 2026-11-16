package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.view.BlockStatusView;
import com.campusbridge.entity.UserBlock;
import com.campusbridge.mapper.UserBlockMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/blocks")
@RequiredArgsConstructor
public class BlockController {
    private final UserBlockMapper userBlockMapper;
    private final UserMapper userMapper;

    @PostMapping("/{blockedUserId}")
    public ApiResponse<String> blockUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long blockedUserId) {
        Long currentUserId = principal.getUserId();
        if (currentUserId.equals(blockedUserId)) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "不能拉黑自己");
        }
        if (userMapper.selectById(blockedUserId) == null) {
            throw new BusinessException(ErrorCode.USER_001_NOT_FOUND);
        }
        if (userBlockMapper.exists(currentUserId, blockedUserId)) {
            return ApiResponse.success("已经拉黑该用户");
        }
        UserBlock block = new UserBlock();
        block.setBlockerId(currentUserId);
        block.setBlockedId(blockedUserId);
        block.setCreatedAt(LocalDateTime.now());
        try {
            userBlockMapper.insert(block);
        } catch (DuplicateKeyException ignored) {
            return ApiResponse.success("已经拉黑该用户");
        }
        return ApiResponse.success("已拉黑该用户");
    }

    @DeleteMapping("/{blockedUserId}")
    public ApiResponse<String> unblockUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long blockedUserId) {
        Long currentUserId = principal.getUserId();
        userBlockMapper.delete(currentUserId, blockedUserId);
        return ApiResponse.success("已取消拉黑");
    }

    @GetMapping
    public ApiResponse<List<UserBlock>> getBlockedUsers(@AuthenticationPrincipal UserPrincipal principal) {
        Long currentUserId = principal.getUserId();
        List<UserBlock> blocks = userBlockMapper.selectBlockedIds(currentUserId);
        return ApiResponse.success(blocks);
    }

    @GetMapping("/check/{targetUserId}")
    public ApiResponse<BlockStatusView> checkBlock(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long targetUserId) {
        Long currentUserId = principal.getUserId();
        boolean blocked = userBlockMapper.exists(currentUserId, targetUserId);
        return ApiResponse.success(new BlockStatusView(blocked));
    }
}
