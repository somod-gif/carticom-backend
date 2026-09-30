package com.carticom.dto.businessowner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessOwnerDashboardDTO {
    private BigDecimal pendingRevenue;
    private BigDecimal availableRevenue;
    private BigDecimal lifetimeRevenue;
    private Long pendingOrders;
    private List<OrderSummaryDTO> recentOrders;
}
