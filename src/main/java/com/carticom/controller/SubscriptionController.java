package com.carticom.controller;

import com.carticom.dto.common.SuccessResponse;
import com.carticom.dto.subscription.PlanResponse;
import com.carticom.dto.subscription.SubscribeRequest;
import com.carticom.dto.subscription.SubscriptionResponse;
import com.carticom.dto.subscription.UpgradeResponse;
import com.carticom.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @GetMapping
    @Operation(summary = "List caller's subscriptions")
    public ResponseEntity<List<SubscriptionResponse>> listSubscriptions(Authentication authentication) {
        try {
            return ResponseEntity.ok(List.of(subscriptionService.getCurrentSubscription(authentication.getName())));
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/plans")
    @Operation(summary = "Get all available plans")
    public ResponseEntity<SuccessResponse<List<PlanResponse>>> getPlans() {
        return ResponseEntity.ok(new SuccessResponse<>(true, subscriptionService.getAllPlans()));
    }

    @PostMapping("/subscribe")
    @Operation(summary = "Subscribe to a plan",
            description = "FREE activates immediately; paid plans return a checkout authorization URL")
    public ResponseEntity<UpgradeResponse> subscribe(
            Authentication authentication,
            @Valid @RequestBody SubscribeRequest request) {
        return ResponseEntity.ok(subscriptionService.startUpgrade(authentication.getName(), request));
    }

    @GetMapping("/current")
    @Operation(summary = "Get current subscription for authenticated user's store")
    public ResponseEntity<SubscriptionResponse> getCurrentSubscription(Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.getCurrentSubscription(authentication.getName()));
    }

    @PatchMapping("/upgrade")
    @Operation(summary = "Upgrade/downgrade subscription plan",
            description = "FREE activates immediately; paid plans return a checkout authorization URL")
    public ResponseEntity<UpgradeResponse> changePlan(
            Authentication authentication,
            @Valid @RequestBody SubscribeRequest request) {
        return ResponseEntity.ok(subscriptionService.startUpgrade(authentication.getName(), request));
    }

    @GetMapping("/upgrade/verify/{reference}")
    @Operation(summary = "Verify a subscription payment and activate the plan")
    public ResponseEntity<UpgradeResponse> verifyUpgrade(
            Authentication authentication,
            @PathVariable String reference) {
        return ResponseEntity.ok(subscriptionService.verifyUpgrade(authentication.getName(), reference));
    }

    @GetMapping("/check")
    @Operation(summary = "Check if store has access to a feature")
    public ResponseEntity<Boolean> checkFeature(
            Authentication authentication,
            @RequestParam String feature) {
        return ResponseEntity.ok(subscriptionService.hasFeatureAccess(authentication.getName(), feature));
    }
}
