package com.carticom.dto.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanResponse {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer maxProducts;
    private Integer maxOrdersPerMonth;
    private Integer maxCustomers;
    private Boolean hasAnalytics;
    private Boolean hasAiFeatures;
    private Boolean hasPrioritySupport;
    private Boolean hasCustomDomain;
    private Boolean hasRemoveBranding;
}
