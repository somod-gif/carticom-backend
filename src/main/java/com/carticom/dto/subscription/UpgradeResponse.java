package com.carticom.dto.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeResponse {

    private String planName;
    private BigDecimal amount;
    private String status;
    private String reference;
    private String authorizationUrl;
    private String provider;
    private String message;
}
