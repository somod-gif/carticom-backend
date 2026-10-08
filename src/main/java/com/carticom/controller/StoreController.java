package com.carticom.controller;

import com.carticom.dto.image.ImageUploadResponse;
import com.carticom.dto.store.CreateStoreRequest;
import com.carticom.dto.store.StoreResponse;
import com.carticom.dto.store.UpdateStoreBrandingRequest;
import com.carticom.dto.store.UpdateStoreSettingsRequest;
import com.carticom.service.AuditService;
import com.carticom.service.ByteshipService;
import com.carticom.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
@Tag(name = "Stores", description = "Store management endpoints")
public class StoreController {

    private final StoreService storeService;
    private final ByteshipService byteshipService;
    private final AuditService auditService;

    @PostMapping
    @Operation(summary = "Create a new store", description = "Creates a store for the authenticated user with auto-generated slug")
    @ApiResponse(responseCode = "201", description = "Store created successfully")
    @ApiResponse(responseCode = "400", description = "Validation error")
    public ResponseEntity<StoreResponse> createStore(
            Authentication authentication,
            @Valid @RequestBody CreateStoreRequest request) {
        StoreResponse response = storeService.createStore(authentication.getName(), request);
        audit(() -> auditService.storeCreated(authentication.getName(), roleOf(authentication),
                response.getId(), response.getName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user's store", description = "Returns the store details for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Store found")
    @ApiResponse(responseCode = "404", description = "No store found for user")
    public ResponseEntity<StoreResponse> getMyStore(Authentication authentication) {
        StoreResponse response = storeService.getStoreByUser(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/all")
    @Operation(summary = "Get all current user's stores", description = "Returns the store details for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Stores found")
    public ResponseEntity<List<StoreResponse>> getAllMyStores(Authentication authentication) {
        List<StoreResponse> response = storeService.getAllStoresByUser(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get current user's stores (list alias)")
    public ResponseEntity<List<StoreResponse>> listMyStores(Authentication authentication) {
        return ResponseEntity.ok(storeService.getAllStoresByUser(authentication.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get store by ID")
    public ResponseEntity<StoreResponse> getStoreById(
            Authentication authentication,
            @PathVariable Long id) {
        StoreResponse response = storeService.getStoreByUser(authentication.getName());
        if (!response.getId().equals(id)) {
            throw new com.carticom.exception.ResourceNotFoundException("Store not found");
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/settings")
    @Operation(summary = "Get store settings")
    public ResponseEntity<StoreResponse> getStoreSettings(
            Authentication authentication,
            @PathVariable Long id) {
        StoreResponse response = storeService.getStoreByUser(authentication.getName());
        if (!response.getId().equals(id)) {
            throw new com.carticom.exception.ResourceNotFoundException("Store not found");
        }
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update store (onboarding)")
    public ResponseEntity<StoreResponse> updateStore(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body) {
        StoreResponse response = storeService.updateStore(authentication.getName(), id, body);
        audit(() -> auditService.storeUpdated(authentication.getName(), roleOf(authentication),
                response.getId(), response.getName()));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/branding")
    @Operation(summary = "Update storefront branding",
            description = "Applies validated branding - template, colours, socials, SEO, "
                    + "custom CSS, announcement bar and section order - to a store")
    @ApiResponse(responseCode = "200", description = "Branding updated")
    @ApiResponse(responseCode = "400", description = "Validation error or unsafe customCss/sectionConfig")
    @ApiResponse(responseCode = "404", description = "Store not found or not owned by caller")
    public ResponseEntity<StoreResponse> updateBranding(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateStoreBrandingRequest request) {
        StoreResponse response = storeService.updateBranding(authentication.getName(), id, request);
        audit(() -> auditService.brandingChanged(authentication.getName(), roleOf(authentication),
                response.getId(), response.getName()));
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update store status", description = "Sets store status (ACTIVE, INACTIVE, PENDING)")
    public ResponseEntity<StoreResponse> updateStoreStatus(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam String status) {
        StoreResponse response = storeService.setStatus(authentication.getName(), id, status);
        audit(() -> auditService.statusChanged(authentication.getName(), roleOf(authentication),
                response.getId(), response.getName(), status));
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/publish")
    @Operation(summary = "Publish store", description = "Sets store status to ACTIVE")
    public ResponseEntity<StoreResponse> publishStore(
            Authentication authentication,
            @PathVariable Long id) {
        StoreResponse response = storeService.setStatus(authentication.getName(), id, "ACTIVE");
        audit(() -> auditService.statusChanged(authentication.getName(), roleOf(authentication),
                response.getId(), response.getName(), "ACTIVE"));
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/unpublish")
    @Operation(summary = "Unpublish store", description = "Sets store status to INACTIVE")
    public ResponseEntity<StoreResponse> unpublishStore(
            Authentication authentication,
            @PathVariable Long id) {
        StoreResponse response = storeService.setStatus(authentication.getName(), id, "INACTIVE");
        audit(() -> auditService.statusChanged(authentication.getName(), roleOf(authentication),
                response.getId(), response.getName(), "INACTIVE"));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/settings")
    @Operation(summary = "Update store settings",
            description = "Updates name, category, storefront theme and layout for the user's store")
    @ApiResponse(responseCode = "200", description = "Settings updated")
    @ApiResponse(responseCode = "400", description = "Unknown theme/layout or validation error")
    public ResponseEntity<StoreResponse> updateSettings(
            Authentication authentication,
            @Valid @RequestBody UpdateStoreSettingsRequest request) {
        StoreResponse response = storeService.updateSettings(authentication.getName(), request);
        audit(() -> auditService.storeUpdated(authentication.getName(), roleOf(authentication),
                response.getId(), response.getName()));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/settings")
    @Operation(summary = "Update settings for a specific store",
            description = "Updates business info, notification preferences, theme and layout")
    @ApiResponse(responseCode = "200", description = "Settings updated")
    @ApiResponse(responseCode = "404", description = "Store not found or not owned by caller")
    public ResponseEntity<StoreResponse> updateStoreSettings(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateStoreSettingsRequest request) {
        StoreResponse response = storeService.updateSettings(authentication.getName(), id, request);
        audit(() -> auditService.storeUpdated(authentication.getName(), roleOf(authentication),
                response.getId(), response.getName()));
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload store logo")
    @ApiResponse(responseCode = "200", description = "Logo uploaded and saved")
    @ApiResponse(responseCode = "404", description = "Store not found or not owned by caller")
    public ResponseEntity<StoreResponse> uploadLogo(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        ImageUploadResponse uploaded = byteshipService.uploadImage(file);
        StoreResponse response = storeService.setLogo(authentication.getName(), id, uploaded.getUrl());
        audit(() -> auditService.brandingChanged(authentication.getName(), roleOf(authentication),
                id, response.getName()));
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{id}/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload store banner")
    @ApiResponse(responseCode = "200", description = "Banner uploaded and saved")
    @ApiResponse(responseCode = "404", description = "Store not found or not owned by caller")
    public ResponseEntity<StoreResponse> uploadBanner(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        ImageUploadResponse uploaded = byteshipService.uploadImage(file);
        StoreResponse response = storeService.setBanner(authentication.getName(), id, uploaded.getUrl());
        audit(() -> auditService.brandingChanged(authentication.getName(), roleOf(authentication),
                id, response.getName()));
        return ResponseEntity.ok(response);
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
