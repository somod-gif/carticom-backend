package com.carticom.controller;

import com.carticom.dto.admin.AdminOrderResponse;
import com.carticom.dto.admin.AdminStatsResponse;
import com.carticom.dto.admin.AdminStoreResponse;
import com.carticom.dto.admin.AdminUserResponse;
import com.carticom.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Platform administration (requires ADMIN role)")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    @Operation(summary = "Platform stats", description = "Platform-wide counters and revenue")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @GetMapping("/users")
    @Operation(summary = "List users", description = "All registered users with roles")
    public ResponseEntity<List<AdminUserResponse>> getUsers() {
        return ResponseEntity.ok(adminService.getUsers());
    }

    @GetMapping("/stores")
    @Operation(summary = "List stores", description = "All stores on the platform")
    public ResponseEntity<List<AdminStoreResponse>> getStores() {
        return ResponseEntity.ok(adminService.getStores());
    }

    @GetMapping("/orders")
    @Operation(summary = "List orders", description = "Most recent 100 platform orders")
    public ResponseEntity<List<AdminOrderResponse>> getOrders() {
        return ResponseEntity.ok(adminService.getOrders());
    }
}
