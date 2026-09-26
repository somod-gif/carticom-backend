package com.carticom.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductStock {
    private Long productId;
    private String productName;
    private String sku;
    private Integer quantity;
    private Integer reservedQuantity;
}
