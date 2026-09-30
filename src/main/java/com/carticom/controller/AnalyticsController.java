package com.carticom.controller;

import com.carticom.dto.analytics.DashboardResponse;
import com.carticom.model.Store;
import com.carticom.model.User;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import com.carticom.service.*;
import com.carticom.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Analytics and dashboard endpoints")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final BusinessHealthService businessHealthService;
    private final InventoryIntelligenceService inventoryIntelligenceService;
    private final BusinessDataService businessDataService;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final PlanGuard planGuard;

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard analytics", description = "Returns revenue, orders, products, customers, and charts")
    public ResponseEntity<DashboardResponse> getDashboard(Authentication authentication) {
        planGuard.requireFeature(authentication.getName(), "analytics");
        return ResponseEntity.ok(analyticsService.getDashboard(authentication.getName()));
    }

    @GetMapping("/health-score")
    @Operation(summary = "Get business health score", description = "Returns 0-100 health score with dimension breakdown")
    public ResponseEntity<Map<String, Object>> getHealthScore(Authentication authentication) {
        planGuard.requireFeature(authentication.getName(), "analytics");
        Store store = getStoreBySeller(authentication.getName());
        return ResponseEntity.ok(businessHealthService.computeHealthScore(store.getId()));
    }

    @GetMapping("/inventory-health")
    @Operation(summary = "Get inventory health", description = "Returns stockout predictions and reorder recommendations")
    public ResponseEntity<?> getInventoryHealth(Authentication authentication) {
        planGuard.requireFeature(authentication.getName(), "analytics");
        Store store = getStoreBySeller(authentication.getName());
        return ResponseEntity.ok(inventoryIntelligenceService.getInventoryHealth(store.getId()));
    }

    @GetMapping("/business-context")
    @Operation(summary = "Get full business context", description = "Returns all business data for AI analysis")
    public ResponseEntity<Map<String, Object>> getBusinessContext(Authentication authentication) {
        planGuard.requireFeature(authentication.getName(), "analytics");
        Store store = getStoreBySeller(authentication.getName());
        return ResponseEntity.ok(businessDataService.getFullBusinessContext(store.getId()));
    }

    private Store getStoreBySeller(String sellerEmail) {
        User user = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return storeRepository.findBySellerId(user.getId())
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No store found"));
    }
}
