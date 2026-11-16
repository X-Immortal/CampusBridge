package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.query.NotificationQuery;
import com.campusbridge.dto.view.NotificationView;
import com.campusbridge.dto.view.ReadAllNotificationsView;
import com.campusbridge.dto.view.UnreadCountView;
import com.campusbridge.service.NotificationService;
import com.campusbridge.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PagedResult<NotificationView>> list(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @ModelAttribute NotificationQuery query) {
        return ApiResponse.success(notificationService.list(
                principal.getUserId(), query));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountView> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(new UnreadCountView(notificationService.countUnread(
                principal.getUserId())));
    }

    @PostMapping("/{notificationId}/read")
    public ApiResponse<NotificationView> markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long notificationId) {
        return ApiResponse.success(notificationService.markRead(
                principal.getUserId(), notificationId));
    }

    @PostMapping("/read-all")
    public ApiResponse<ReadAllNotificationsView> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(notificationService.markAllRead(
                principal.getUserId()));
    }
}
