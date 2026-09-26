package com.carticom.controller;

import com.carticom.dto.delivery.CreateDeliveryRequest;
import com.carticom.dto.delivery.DeliveryResponse;
import com.carticom.service.DeliveryService;
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
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
@Tag(name = "Deliveries", description = "Delivery tracking and management")
public class DeliveryController {

    private final DeliveryService deliveryService;

    @PostMapping
    @Operation(summary = "Create a delivery", description = "Create delivery for an order")
    public ResponseEntity<DeliveryResponse> createDelivery(
            Authentication authentication,
            @Valid @RequestBody CreateDeliveryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deliveryService.createDelivery(authentication.getName(), request));
    }

    @GetMapping
    @Operation(summary = "Get all deliveries")
    public ResponseEntity<List<DeliveryResponse>> getDeliveries(Authentication authentication) {
        return ResponseEntity.ok(deliveryService.getDeliveries(authentication.getName()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update delivery status")
    public ResponseEntity<DeliveryResponse> updateStatus(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(deliveryService.updateStatus(authentication.getName(), id, status));
    }

    @GetMapping("/tracking/{trackingNumber}")
    @Operation(summary = "Track delivery", description = "Public tracking by tracking number")
    public ResponseEntity<DeliveryResponse> track(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(deliveryService.getByTrackingNumber(trackingNumber));
    }
}
