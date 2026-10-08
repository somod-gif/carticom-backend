package com.carticom.dto.payout;

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
public class PayoutTransactionResponse {
    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime paidAt;
}
