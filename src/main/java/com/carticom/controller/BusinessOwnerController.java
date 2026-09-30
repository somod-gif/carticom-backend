package com.carticom.controller;

import com.carticom.dto.businessowner.AnalyticsPointDTO;
import com.carticom.dto.businessowner.BusinessOwnerDashboardDTO;
import com.carticom.dto.businessowner.UpdateProfileRequest;
import com.carticom.service.BusinessOwnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/business-owner")
@RequiredArgsConstructor
@Tag(name = "Business Owner", description = "Merchant dashboard, analytics and profile")
public class BusinessOwnerController {

    private final BusinessOwnerService businessOwnerService;

    @GetMapping("/dashboard")
    @Operation(summary = "Merchant dashboard summary", description = "Revenue KPIs and recent orders for the signed-in merchant")
    public ResponseEntity<BusinessOwnerDashboardDTO> getDashboard(Authentication authentication) {
        return ResponseEntity.ok(businessOwnerService.getDashboard(authentication.getName()));
    }

    @GetMapping("/analytics/revenue")
    @Operation(summary = "Revenue analytics", description = "Revenue series bucketed by period")
    public ResponseEntity<List<AnalyticsPointDTO>> revenue(Authentication authentication,
                                                           @RequestParam(defaultValue = "monthly") String period) {
        return ResponseEntity.ok(businessOwnerService.getAnalytics(authentication.getName(), period));
    }

    @GetMapping("/analytics/orders")
    @Operation(summary = "Orders analytics", description = "Order counts bucketed by period")
    public ResponseEntity<List<AnalyticsPointDTO>> orders(Authentication authentication,
                                                          @RequestParam(defaultValue = "monthly") String period) {
        return ResponseEntity.ok(businessOwnerService.getAnalytics(authentication.getName(), period));
    }

    @GetMapping("/analytics/customers")
    @Operation(summary = "Customers analytics", description = "Active customer counts bucketed by period")
    public ResponseEntity<List<AnalyticsPointDTO>> customers(Authentication authentication,
                                                             @RequestParam(defaultValue = "monthly") String period) {
        return ResponseEntity.ok(businessOwnerService.getAnalytics(authentication.getName(), period));
    }

    @GetMapping("/analytics/conversion")
    @Operation(summary = "Conversion analytics", description = "Conversion series bucketed by period")
    public ResponseEntity<List<AnalyticsPointDTO>> conversion(Authentication authentication,
                                                              @RequestParam(defaultValue = "monthly") String period) {
        return ResponseEntity.ok(businessOwnerService.getAnalytics(authentication.getName(), period));
    }

    @GetMapping("/analytics/weekly")
    @Operation(summary = "Weekly analytics", description = "Daily buckets for the last 7 days")
    public ResponseEntity<List<AnalyticsPointDTO>> weekly(Authentication authentication) {
        return ResponseEntity.ok(businessOwnerService.getAnalytics(authentication.getName(), "weekly"));
    }

    @GetMapping("/analytics/monthly")
    @Operation(summary = "Monthly analytics", description = "Monthly buckets for the last 12 months")
    public ResponseEntity<List<AnalyticsPointDTO>> monthly(Authentication authentication) {
        return ResponseEntity.ok(businessOwnerService.getAnalytics(authentication.getName(), "monthly"));
    }

    @GetMapping("/analytics/yearly")
    @Operation(summary = "Yearly analytics", description = "Yearly buckets for the last 5 years")
    public ResponseEntity<List<AnalyticsPointDTO>> yearly(Authentication authentication) {
        return ResponseEntity.ok(businessOwnerService.getAnalytics(authentication.getName(), "yearly"));
    }

    @GetMapping("/profile")
    @Operation(summary = "Merchant profile", description = "Profile of the signed-in merchant")
    public ResponseEntity<Map<String, Object>> getProfile(Authentication authentication) {
        return ResponseEntity.ok(businessOwnerService.getProfile(authentication.getName()));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update merchant profile", description = "Updates name, phone and store business name")
    public ResponseEntity<Map<String, Object>> updateProfile(Authentication authentication,
                                                             @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(businessOwnerService.updateProfile(authentication.getName(), request));
    }
}
