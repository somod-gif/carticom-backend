package com.carticom.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private String reference;
    private BigDecimal amount;
    private String method;
    private String status;
    private String orderNumber;
    private String gatewayResponse;
    private LocalDateTime createdAt;
}
