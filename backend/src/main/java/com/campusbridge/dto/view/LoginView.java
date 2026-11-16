package com.campusbridge.dto.view;

import com.campusbridge.enums.UserRole;

public record LoginView(Long userId, String username, UserRole role, String token, String refreshToken) {
}
