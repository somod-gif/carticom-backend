package com.carticom.controller;

import com.carticom.dto.order.OrderResponse;
import com.carticom.model.User;
import com.carticom.repository.UserRepository;
import com.carticom.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "My Account", description = "Logged-in user profile and cross-store order history")
public class MeController {

    private final UserRepository userRepository;
    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "Get my profile", description = "Returns the profile of the authenticated user")
    public ResponseEntity<Map<String, Object>> getMe(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow();
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "role", user.getRole().name(),
                "phone", user.getPhone() != null ? user.getPhone() : "",
                "createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : ""
        ));
    }

    @GetMapping("/orders")
    @Operation(summary = "Get my orders", description = "All orders placed with this email across every store")
    public ResponseEntity<List<OrderResponse>> getMyOrders(Authentication authentication) {
        return ResponseEntity.ok(orderService.getOrdersForCustomerEmail(authentication.getName()));
    }
}
