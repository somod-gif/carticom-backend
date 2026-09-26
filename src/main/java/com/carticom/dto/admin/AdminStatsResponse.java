package com.carticom.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminStatsResponse {
    private Long totalUsers;
    private Long totalVendors;
    private Long totalStaff;
    private Long totalCustomers;
    private Long totalAdmins;
    private Long totalStores;
    private Long totalOrders;
    private Long paidOrders;
    private BigDecimal totalRevenue;
}
