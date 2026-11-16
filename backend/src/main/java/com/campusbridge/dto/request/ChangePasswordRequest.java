package com.campusbridge.dto.request;

public record ChangePasswordRequest(String oldPassword, String newPassword) {
}
