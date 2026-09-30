package com.carticom.dto.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {
    private Long id;
    private Long storeId;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private Integer stockQuantity;
    private Integer stock;
    private Integer quantity;
    private String sku;
    private String barcode;
    private String imageUrl;
    private String category;
    private Boolean isActive;
    private Boolean active;
    private Boolean isFeatured;
    private Boolean digital;
    private String currency;
    private String tenantId;
    private BigDecimal weight;
    private String unit;
    private Integer lowStockThreshold;
    private Integer soldCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isLowStock;
}
