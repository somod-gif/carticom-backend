package com.carticom.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopProduct {
    private Long id;
    private String name;
    private Integer soldCount;
    private BigDecimal revenue;
    private String imageUrl;
}
