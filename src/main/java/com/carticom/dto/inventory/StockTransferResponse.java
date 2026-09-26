package com.carticom.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferResponse {
    private String message;
    private String productName;
    private String fromLocation;
    private String toLocation;
    private Integer quantity;
}
