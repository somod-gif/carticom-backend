package com.carticom.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationStockSummary {
    private Long locationId;
    private String locationName;
    private Integer totalProducts;
    private List<ProductStock> products;
}
