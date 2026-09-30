package com.carticom.dto.auth;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
