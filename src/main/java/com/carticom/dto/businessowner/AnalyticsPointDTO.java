package com.carticom.dto.businessowner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsPointDTO {
    private String period;
    private BigDecimal revenue;
    private Long orders;
    private Long customers;
    private Double conversionRate;
    private Map<String, Object> changes;
}
