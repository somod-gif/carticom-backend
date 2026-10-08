package com.carticom.service;

import com.carticom.dto.auth.AuthResponse;
import com.carticom.model.Role;
import com.carticom.model.User;
import com.carticom.repository.PasswordResetTokenRepository;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import com.carticom.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Refresh-token rotation: a refresh token may be redeemed exactly
 * once. Re-presenting a rotated token must fail closed (null =>
 * the controller answers 401 and clears the cookie).
 */
class AuthServiceRefreshRotationTest {

    private AuthService service;
    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret",
                Base64.getEncoder().encodeToString(new byte[48]));
        ReflectionTestUtils.setField(tokenProvider, "jwtExpiration", 86400000L);

        User user = new User();
        user.setId(1L);
        user.setEmail("seller@carticom.cv");
        user.setRole(Role.VENDOR);
        user.setOnboardingCompleted(true);

        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail("seller@carticom.cv"))
                .thenReturn(Optional.of(user));

        StoreRepository storeRepository = mock(StoreRepository.class);
        when(storeRepository.findBySellerId(1L))
                .thenReturn(Collections.emptyList());

        service = new AuthService(
                userRepository,
                mock(PasswordEncoder.class),
                mock(AuthenticationManager.class),
                tokenProvider,
                mock(PasswordResetTokenRepository.class),
                mock(SendByteService.class),
                storeRepository,
                mock(AuditService.class));
        ReflectionTestUtils.setField(service, "jwtExpiration", 86400000L);
        ReflectionTestUtils.setField(service, "baseUrl", "https://carticom.cv");
    }

    @Test
    void rotatesTheRefreshTokenAndRejectsReuse() {
        String token = tokenProvider.generateToken("seller@carticom.cv");

        AuthResponse first = service.refresh(token);
        assertNotNull(first, "the first redemption must succeed");
        assertEquals("seller@carticom.cv", first.getEmail());
        assertNotNull(first.getRefreshToken());

        assertNull(service.refresh(token),
                "a rotated refresh token must not be reusable");

        AuthResponse second = service.refresh(first.getRefreshToken());
        assertNotNull(second,
                "the newly issued refresh token must work for the next refresh");
    }

    @Test
    void rejectsUnknownAndInvalidTokens() {
        assertNull(service.refresh(null));
        assertNull(service.refresh(""));
        assertNull(service.refresh("not-a-jwt"));
    }
}
