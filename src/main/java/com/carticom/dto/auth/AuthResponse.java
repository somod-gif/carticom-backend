package com.carticom.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private Long userId;
    private String token;
    private String accessToken;
    private String refreshToken;
    private String email;
    private String fullName;
    private String role;
    private Long expiresIn;
    private String tokenType;
}
