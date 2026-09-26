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
public class PaymentVerifyResponse {
    private String reference;
    private String status;
    private BigDecimal amount;
    private String provider;
    private String gatewayResponse;
    private Boolean verified;
}
