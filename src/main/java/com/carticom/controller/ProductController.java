package com.carticom.controller;

import com.carticom.dto.product.CreateProductRequest;
import com.carticom.dto.product.ProductResponse;
import com.carticom.service.ProductService;
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
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management endpoints")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @Operation(summary = "Create a new product")
    public ResponseEntity<ProductResponse> createProduct(
            Authentication authentication,
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = productService.createProduct(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get all products")
    public ResponseEntity<List<ProductResponse>> getProducts(Authentication authentication) {
        return ResponseEntity.ok(productService.getProducts(authentication.getName()));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active products only")
    public ResponseEntity<List<ProductResponse>> getActiveProducts(Authentication authentication) {
        return ResponseEntity.ok(productService.getActiveProducts(authentication.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ProductResponse> getProduct(
            Authentication authentication,
            @PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(authentication.getName(), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product")
    public ResponseEntity<ProductResponse> updateProduct(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(authentication.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product")
    public ResponseEntity<Void> deleteProduct(
            Authentication authentication,
            @PathVariable Long id) {
        productService.deleteProduct(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Get low stock products")
    public ResponseEntity<List<ProductResponse>> getLowStockProducts(Authentication authentication) {
        return ResponseEntity.ok(productService.getLowStockProducts(authentication.getName()));
    }
}
