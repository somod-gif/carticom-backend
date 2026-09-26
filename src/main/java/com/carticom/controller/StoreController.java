package com.carticom.controller;

import com.carticom.dto.store.CreateStoreRequest;
import com.carticom.dto.store.StoreResponse;
import com.carticom.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
@Tag(name = "Stores", description = "Store management endpoints")
public class StoreController {

    private final StoreService storeService;

    @PostMapping
    @Operation(summary = "Create a new store", description = "Creates a store for the authenticated user with auto-generated slug")
    @ApiResponse(responseCode = "201", description = "Store created successfully")
    @ApiResponse(responseCode = "400", description = "Validation error")
    public ResponseEntity<StoreResponse> createStore(
            Authentication authentication,
            @Valid @RequestBody CreateStoreRequest request) {
        StoreResponse response = storeService.createStore(authentication.getName(), request);
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
    @Operation(summary = "Get all current user's stores", description = "Returns all stores for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Stores found")
    public ResponseEntity<List<StoreResponse>> getAllMyStores(Authentication authentication) {
        List<StoreResponse> response = storeService.getAllStoresByUser(authentication.getName());
        return ResponseEntity.ok(response);
    }
}
