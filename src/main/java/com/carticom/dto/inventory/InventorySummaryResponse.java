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
public class InventorySummaryResponse {
    private Integer totalLocations;
    private Integer totalProducts;
    private Integer lowStockCount;
    private List<LocationStockSummary> locations;
}
