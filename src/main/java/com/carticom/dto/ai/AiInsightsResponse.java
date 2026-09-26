package com.carticom.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiInsightsResponse {
    private String salesSummary;
    private List<String> recommendations;
    private String inventoryAlert;
    private String growthTip;
}
