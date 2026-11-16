package com.campusbridge.dto.view;

public record LoginView(Long userId, String username, String role, String token, String refreshToken) {
}
