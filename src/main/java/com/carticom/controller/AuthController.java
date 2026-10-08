package com.carticom.controller;

import com.carticom.dto.auth.AuthResponse;
import com.carticom.dto.auth.ForgotPasswordRequest;
import com.carticom.dto.auth.LoginRequest;
import com.carticom.dto.auth.MessageResponse;
import com.carticom.dto.auth.RegisterRequest;
import com.carticom.dto.auth.ResetPasswordRequest;
import com.carticom.dto.businessowner.UpdateProfileRequest;
import com.carticom.dto.common.SuccessResponse;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.User;
import com.carticom.repository.UserRepository;
import com.carticom.service.AuditService;
import com.carticom.service.AuthService;
import com.carticom.service.BusinessOwnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration and login")
public class AuthController {

    private static final String REFRESH_COOKIE = "carticom_refresh";

    private final AuthService authService;
    private final BusinessOwnerService businessOwnerService;
    private final UserRepository userRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final AuditService auditService;

    @Value("${app.base-url}")
    private String baseUrl;

    private ResponseCookie refreshCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .sameSite("Lax")
                .secure(baseUrl != null && baseUrl.startsWith("https"))
                .build();
    }

    private ResponseCookie clearedRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .secure(baseUrl != null && baseUrl.startsWith("https"))
                .build();
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account and returns JWT token")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Email already exists or validation error")
    public ResponseEntity<SuccessResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        audit(() -> auditService.userRegistered(response.getEmail(), response.getRole()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(response.getRefreshToken(), response.getExpiresIn()).toString())
                .body(new SuccessResponse<>(true, response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticates user and returns JWT token")
    @ApiResponse(responseCode = "200", description = "Login successful")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<SuccessResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        audit(() -> auditService.userLoggedIn(response.getEmail(), response.getRole()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(response.getRefreshToken(), response.getExpiresIn()).toString())
                .body(new SuccessResponse<>(true, response));
    }

    public record RefreshRequest(String refreshToken, String accessToken) {
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh session", description = "Exchanges a valid refresh/access token or HttpOnly cookie for a new token pair")
    @ApiResponse(responseCode = "200", description = "Token refreshed")
    @ApiResponse(responseCode = "401", description = "Invalid or expired token")
    public ResponseEntity<SuccessResponse<AuthResponse>> refresh(
            @RequestBody(required = false) String rawBody,
            @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken) {
        String token = null;
        if (rawBody != null && !rawBody.isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(rawBody);
                String refresh = node.path("refreshToken").asText(null);
                String access = node.path("accessToken").asText(null);
                token = refresh != null && !refresh.isBlank() ? refresh : access;
            } catch (Exception ignored) {
                // Unparseable body — fall back to the cookie
            }
        }
        if (token == null || token.isBlank()) {
            token = cookieToken;
        }
        AuthResponse response = authService.refresh(token);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                    .build();
        }
        audit(() -> auditService.sessionRefreshed(response.getEmail(), response.getRole()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(response.getRefreshToken(), response.getExpiresIn()).toString())
                .body(new SuccessResponse<>(true, response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Clears the HttpOnly refresh cookie")
    public ResponseEntity<Void> logout(Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "anonymous";
        audit(() -> auditService.userLoggedOut(actor, roleOf(authentication)));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns the profile of the authenticated user")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "role", user.getRole().name(),
                "phone", user.getPhone() != null ? user.getPhone() : "",
                "profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "",
                "createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : "",
                "onboardingCompleted", authService.isOnboardingCompleted(user)
        ));
    }

    @PostMapping("/onboarding/complete")
    @Operation(summary = "Mark onboarding complete", description = "Called by the setup wizard when the merchant finishes (or already has) a store")
    public ResponseEntity<Map<String, Object>> completeOnboarding(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setOnboardingCompleted(true);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "onboardingCompleted", true));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update current user", description = "Updates fullName, phone, business name and profile image")
    public ResponseEntity<Map<String, Object>> updateProfile(Authentication authentication,
                                                             @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(businessOwnerService.updateProfile(authentication.getName(), request));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset link",
            description = "Always returns 200. If the email exists, a reset link is sent.")
    @ApiResponse(responseCode = "200", description = "Request accepted")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(new MessageResponse(
                "If an account exists for that email, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with token", description = "Sets a new password using a valid reset token")
    @ApiResponse(responseCode = "200", description = "Password updated")
    @ApiResponse(responseCode = "400", description = "Invalid, used or expired token")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getPassword());
        return ResponseEntity.ok(new MessageResponse("Password updated successfully"));
    }

    @PutMapping("/password")
    @Operation(summary = "Change current user's password",
            description = "Verifies the current password and sets a new one (min 8 characters)")
    @ApiResponse(responseCode = "200", description = "Password updated")
    @ApiResponse(responseCode = "400", description = "Wrong current password or weak new password")
    public ResponseEntity<MessageResponse> changePassword(
            Authentication authentication,
            @RequestBody com.carticom.dto.auth.ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), request);
        audit(() -> auditService.passwordChanged(authentication.getName(), roleOf(authentication)));
        return ResponseEntity.ok(new MessageResponse("Password updated successfully"));
    }

    // ── Audit logging ───────────────────────────────────────────
    // Audit failures must never break the main request flow.

    private void audit(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Audit log failed: {}", e.getMessage());
        }
    }

    private String roleOf(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null
                || authentication.getAuthorities().isEmpty()) {
            return "UNKNOWN";
        }
        String authority = authentication.getAuthorities().iterator().next().getAuthority();
        if (authority == null) {
            return "UNKNOWN";
        }
        return authority.startsWith("ROLE_") ? authority.substring("ROLE_".length()) : authority;
    }
}
