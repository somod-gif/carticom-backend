package com.carticom.service;

import com.carticom.dto.auth.AuthResponse;
import com.carticom.dto.auth.LoginRequest;
import com.carticom.dto.auth.RegisterRequest;
import com.carticom.exception.BadRequestException;
import com.carticom.model.PasswordResetToken;
import com.carticom.model.Role;
import com.carticom.model.User;
import com.carticom.repository.PasswordResetTokenRepository;
import com.carticom.repository.UserRepository;
import com.carticom.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SendByteService sendByteService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        Role role = parseRegistrationRole(request.getRole());

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .build();

        userRepository.save(user);
        log.info("User registered: {} as {}", user.getEmail(), role);

        String token = jwtTokenProvider.generateToken(user.getEmail());

        return buildResponse(user, token);
    }

    private Role parseRegistrationRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return Role.VENDOR;
        }
        Role requested;
        try {
            requested = Role.valueOf(rawRole.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + rawRole);
        }
        if (requested != Role.VENDOR && requested != Role.CUSTOMER) {
            throw new BadRequestException("Role must be VENDOR or CUSTOMER");
        }
        return requested;
    }

    public AuthResponse refresh(String token) {
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            return null;
        }
        String email = jwtTokenProvider.getEmailFromToken(token);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return null;
        }
        String newToken = jwtTokenProvider.generateToken(user.getEmail());
        return buildResponse(user, newToken);
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        String token = jwtTokenProvider.generateToken(authentication);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Invalid credentials"));

        return buildResponse(user, token);
    }

    private AuthResponse buildResponse(User user, String token) {
        return AuthResponse.builder()
                .userId(user.getId())
                .token(token)
                .accessToken(token)
                .refreshToken(token)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .expiresIn(jwtExpiration / 1000)
                .tokenType("Bearer")
                .build();
    }

    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String tokenValue = UUID.randomUUID().toString().replace("-", "");
            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .user(user)
                    .token(tokenValue)
                    .expiresAt(LocalDateTime.now().plusMinutes(30))
                    .used(false)
                    .build());

            String resetUrl = baseUrl + "/reset-password?token=" + tokenValue;
            try {
                sendByteService.send(
                        user.getEmail(),
                        "Reset your Carticom password",
                        resetEmailHtml(user.getFullName(), resetUrl));
                log.info("Password reset email sent to {}", user.getEmail());
            } catch (Exception e) {
                log.warn("Could not send reset email to {} ({}). Reset link: {}",
                        user.getEmail(), e.getMessage(), resetUrl);
            }
        });
        log.info("Password reset requested for {}", email);
    }

    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset link"));

        if (resetToken.isUsed() || resetToken.isExpired()) {
            throw new BadRequestException("Invalid or expired reset link");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        log.info("Password reset completed for {}", user.getEmail());
    }

    private String resetEmailHtml(String fullName, String resetUrl) {
        String firstName = fullName == null || fullName.isBlank() ? "there" : fullName.split(" ")[0];
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;color:#13264b;">
                  <div style="font-size:20px;font-weight:800;letter-spacing:-.5px;color:#185bd0;">carticom</div>
                  <h1 style="font-size:24px;margin:24px 0 8px;">Reset your password</h1>
                  <p style="font-size:15px;line-height:1.6;color:#62738f;">Hi %s, we received a request to reset the password for your Carticom account. This link expires in 30 minutes.</p>
                  <a href="%s" style="display:inline-block;margin:24px 0;padding:14px 22px;background:#185bd0;color:#ffffff;text-decoration:none;font-weight:700;border-radius:8px;">Set a new password</a>
                  <p style="font-size:13px;line-height:1.6;color:#8a9ab0;">If you didn't request this, you can safely ignore this email — your password will not change.</p>
                </div>
                """.formatted(firstName, resetUrl);
    }
}
