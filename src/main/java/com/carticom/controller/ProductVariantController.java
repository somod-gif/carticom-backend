package com.carticom.controller;

import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Product;
import com.carticom.model.ProductVariant;
import com.carticom.model.Store;
import com.carticom.repository.ProductRepository;
import com.carticom.repository.ProductVariantRepository;
import com.carticom.service.StoreAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products/{productId}/variants")
@RequiredArgsConstructor
@Tag(name = "Product Variants", description = "Manage product variants (size, colour, etc.)")
public class ProductVariantController {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final StoreAccessService storeAccessService;

    public record VariantResponse(Long id, Long productId, String name, String value,
                                  BigDecimal price, Integer stock, String sku, Boolean isActive) {}

    public record VariantRequest(String name, String value, BigDecimal price,
                                 Integer stock, String sku, Boolean isActive) {}

    @GetMapping
    @Operation(summary = "List variants for a product")
    public ResponseEntity<List<VariantResponse>> list(Authentication authentication,
                                                      @PathVariable Long productId) {
        Product product = requireOwnProduct(authentication.getName(), productId);
        return ResponseEntity.ok(
                variantRepository.findByProductIdOrderByIdAsc(product.getId()).stream()
                        .map(this::toResponse)
                        .toList());
    }

    @PostMapping
    @Operation(summary = "Create a variant")
    public ResponseEntity<VariantResponse> create(Authentication authentication,
                                                  @PathVariable Long productId,
                                                  @RequestBody VariantRequest request) {
        Product product = requireOwnProduct(authentication.getName(), productId);
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("Variant name is required");
        }
        if (request.value() == null || request.value().isBlank()) {
            throw new BadRequestException("Variant value is required");
        }
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .name(request.name())
                .value(request.value())
                .price(request.price())
                .stock(request.stock() != null ? request.stock() : 0)
                .sku(request.sku())
                .isActive(request.isActive() != null ? request.isActive() : true)
                .build();
        variantRepository.save(variant);
        return ResponseEntity.ok(toResponse(variant));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a variant")
    public ResponseEntity<VariantResponse> update(Authentication authentication,
                                                  @PathVariable Long productId,
                                                  @PathVariable Long id,
                                                  @RequestBody VariantRequest request) {
        Product product = requireOwnProduct(authentication.getName(), productId);
        ProductVariant variant = variantRepository.findById(id)
                .filter(v -> v.getProduct().getId().equals(product.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

        if (request.name() != null && !request.name().isBlank()) variant.setName(request.name());
        if (request.value() != null && !request.value().isBlank()) variant.setValue(request.value());
        if (request.price() != null) variant.setPrice(request.price());
        if (request.stock() != null) variant.setStock(request.stock());
        if (request.sku() != null && !request.sku().isBlank()) variant.setSku(request.sku());
        if (request.isActive() != null) variant.setIsActive(request.isActive());

        variantRepository.save(variant);
        return ResponseEntity.ok(toResponse(variant));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a variant")
    public ResponseEntity<Void> delete(Authentication authentication,
                                       @PathVariable Long productId,
                                       @PathVariable Long id) {
        Product product = requireOwnProduct(authentication.getName(), productId);
        ProductVariant variant = variantRepository.findById(id)
                .filter(v -> v.getProduct().getId().equals(product.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        variantRepository.delete(variant);
        return ResponseEntity.noContent().build();
    }

    private Product requireOwnProduct(String email, Long productId) {
        Store store = storeAccessService.resolveStore(email);
        return productRepository.findById(productId)
                .filter(p -> p.getStore() != null && p.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private VariantResponse toResponse(ProductVariant v) {
        return new VariantResponse(
                v.getId(),
                v.getProduct().getId(),
                v.getName(),
                v.getValue(),
                v.getPrice(),
                v.getStock() != null ? v.getStock() : 0,
                v.getSku(),
                v.getIsActive() != null ? v.getIsActive() : true);
    }
}
