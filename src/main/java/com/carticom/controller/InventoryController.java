package com.carticom.controller;

import com.carticom.dto.inventory.*;
import com.carticom.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Multi-location inventory management")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/locations")
    @Operation(summary = "Create inventory location")
    public ResponseEntity<LocationResponse> createLocation(
            Authentication authentication,
            @Valid @RequestBody CreateLocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.createLocation(authentication.getName(), request));
    }

    @GetMapping("/locations")
    @Operation(summary = "Get all locations")
    public ResponseEntity<List<LocationResponse>> getLocations(Authentication authentication) {
        return ResponseEntity.ok(inventoryService.getLocations(authentication.getName()));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer stock between locations")
    public ResponseEntity<StockTransferResponse> transferStock(
            Authentication authentication,
            @Valid @RequestBody StockTransferRequest request) {
        return ResponseEntity.ok(inventoryService.transferStock(authentication.getName(), request));
    }

    @PostMapping("/locations/{locationId}/products/{productId}/add")
    @Operation(summary = "Add stock to location")
    public ResponseEntity<Void> addStock(
            Authentication authentication,
            @PathVariable Long locationId,
            @PathVariable Long productId,
            @RequestParam Integer quantity) {
        inventoryService.addStock(authentication.getName(), productId, locationId, quantity);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/summary")
    @Operation(summary = "Get inventory summary", description = "Overview of all locations and stock levels")
    public ResponseEntity<InventorySummaryResponse> getSummary(Authentication authentication) {
        return ResponseEntity.ok(inventoryService.getSummary(authentication.getName()));
    }
}
