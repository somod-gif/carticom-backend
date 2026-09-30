package com.carticom.controller;

import com.carticom.dto.auth.AuthResponse;
import com.carticom.dto.auth.ForgotPasswordRequest;
import com.carticom.dto.auth.LoginRequest;
import com.carticom.dto.auth.MessageResponse;
import com.carticom.dto.auth.RegisterRequest;
import com.carticom.dto.auth.ResetPasswordRequest;
import com.carticom.dto.common.SuccessResponse;
import com.carticom.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration and login")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account and returns JWT token")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Email already exists or validation error")
    public ResponseEntity<SuccessResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessResponse<>(true, response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticates user and returns JWT token")
    @ApiResponse(responseCode = "200", description = "Login successful")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<SuccessResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(new SuccessResponse<>(true, response));
    }

    public record RefreshRequest(String refreshToken, String accessToken) {
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh session", description = "Exchanges a valid refresh/access token for a new token pair")
    @ApiResponse(responseCode = "200", description = "Token refreshed")
    @ApiResponse(responseCode = "401", description = "Invalid or expired token")
    public ResponseEntity<SuccessResponse<AuthResponse>> refresh(@RequestBody(required = false) RefreshRequest request) {
        String token = request != null
                ? (request.refreshToken() != null && !request.refreshToken().isBlank()
                        ? request.refreshToken()
                        : request.accessToken())
                : null;
        AuthResponse response = authService.refresh(token);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new SuccessResponse<>(true, response));
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
}
