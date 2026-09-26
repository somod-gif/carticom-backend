package com.carticom.controller;

import com.carticom.dto.subscription.PlanResponse;
import com.carticom.dto.subscription.SubscriptionResponse;
import com.carticom.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Plan and subscription management")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    @Operation(summary = "Get all available plans")
    public ResponseEntity<List<PlanResponse>> getPlans() {
        return ResponseEntity.ok(subscriptionService.getAllPlans());
    }

    @PostMapping("/subscribe")
    @Operation(summary = "Subscribe store to a plan")
    public ResponseEntity<SubscriptionResponse> subscribe(
            Authentication authentication,
            @RequestParam String planName) {
        SubscriptionResponse response = subscriptionService.subscribe(authentication.getName(), planName);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/current")
    @Operation(summary = "Get current subscription for authenticated user's store")
    public ResponseEntity<SubscriptionResponse> getCurrentSubscription(Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.getCurrentSubscription(authentication.getName()));
    }

    @PatchMapping("/upgrade")
    @Operation(summary = "Upgrade/downgrade subscription plan")
    public ResponseEntity<SubscriptionResponse> changePlan(
            Authentication authentication,
            @RequestParam String planName) {
        return ResponseEntity.ok(subscriptionService.changePlan(authentication.getName(), planName));
    }

    @GetMapping("/check")
    @Operation(summary = "Check if store has access to a feature")
    public ResponseEntity<Boolean> checkFeature(
            Authentication authentication,
            @RequestParam String feature) {
        return ResponseEntity.ok(subscriptionService.hasFeatureAccess(authentication.getName(), feature));
    }
}
