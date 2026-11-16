package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.CurrentUserResolver;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.query.NotificationQuery;
import com.campusbridge.dto.view.NotificationView;
import com.campusbridge.dto.view.ReadAllNotificationsView;
import com.campusbridge.dto.view.UnreadCountView;
import com.campusbridge.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {
    private final CurrentUserResolver currentUserResolver;
    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PagedResult<NotificationView>> list(HttpServletRequest request,
            @Valid @ModelAttribute NotificationQuery query) {
        return ApiResponse.success(notificationService.list(
                currentUserResolver.requireCurrentUserId(request), query));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountView> unreadCount(HttpServletRequest request) {
        return ApiResponse.success(new UnreadCountView(notificationService.countUnread(
                currentUserResolver.requireCurrentUserId(request))));
    }

    @PostMapping("/{notificationId}/read")
    public ApiResponse<NotificationView> markRead(HttpServletRequest request, @PathVariable Long notificationId) {
        return ApiResponse.success(notificationService.markRead(
                currentUserResolver.requireCurrentUserId(request), notificationId));
    }

    @PostMapping("/read-all")
    public ApiResponse<ReadAllNotificationsView> markAllRead(HttpServletRequest request) {
        return ApiResponse.success(notificationService.markAllRead(
                currentUserResolver.requireCurrentUserId(request)));
    }
}
