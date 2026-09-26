package com.carticom.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentInitResponse {
    private String reference;
    private String authorizationUrl;
    private String accessCode;
    private String provider;
    private BigDecimal amount;
    private String status;
}
