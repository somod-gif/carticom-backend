package com.carticom.dto.product;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be positive")
    private BigDecimal price;

    private BigDecimal compareAtPrice;

    @Min(value = 0, message = "Stock cannot be negative")
    @JsonAlias("stock")
    private Integer stockQuantity = 0;

    private Integer quantity;

    private String sku;

    private String barcode;

    private String imageUrl;

    private String category;

    private Boolean isActive = true;

    private Boolean isFeatured = false;

    private BigDecimal weight;

    private String unit;

    private Integer lowStockThreshold = 5;
}
