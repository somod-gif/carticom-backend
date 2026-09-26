package com.carticom.controller;

import com.carticom.dto.buyer.BuyerOrderRequest;
import com.carticom.dto.buyer.BuyerOrderResponse;
import com.carticom.dto.product.ProductResponse;
import com.carticom.dto.store.StorePublicResponse;
import com.carticom.service.BuyerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/buyer")
@RequiredArgsConstructor
@Tag(name = "Buyer Storefront", description = "Public buyer-facing endpoints (no auth required)")
public class BuyerController {

    private final BuyerService buyerService;

    @GetMapping("/{storeSlug}")
    @Operation(summary = "Get public store info by slug")
    public ResponseEntity<StorePublicResponse> getStore(@PathVariable String storeSlug) {
        return ResponseEntity.ok(buyerService.getPublicStore(storeSlug));
    }

    @GetMapping("/{storeSlug}/products")
    @Operation(summary = "Get all active products for a store")
    public ResponseEntity<List<ProductResponse>> getStoreProducts(@PathVariable String storeSlug) {
        return ResponseEntity.ok(buyerService.getStoreProducts(storeSlug));
    }

    @GetMapping("/{storeSlug}/products/{productId}")
    @Operation(summary = "Get a single product detail for a store")
    public ResponseEntity<ProductResponse> getStoreProduct(
            @PathVariable String storeSlug,
            @PathVariable Long productId) {
        return ResponseEntity.ok(buyerService.getStoreProduct(storeSlug, productId));
    }

    @PostMapping("/{storeSlug}/orders")
    @Operation(summary = "Place an order from the buyer storefront")
    public ResponseEntity<BuyerOrderResponse> createOrder(
            @PathVariable String storeSlug,
            @Valid @RequestBody BuyerOrderRequest request) {
        BuyerOrderResponse response = buyerService.createBuyerOrder(storeSlug, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{storeSlug}/orders/{orderNumber}")
    @Operation(summary = "Track an order by order number")
    public ResponseEntity<BuyerOrderResponse> trackOrder(
            @PathVariable String storeSlug,
            @PathVariable String orderNumber) {
        return ResponseEntity.ok(buyerService.trackOrder(storeSlug, orderNumber));
    }
}
