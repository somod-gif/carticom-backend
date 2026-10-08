package com.carticom.dto.payout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutSummaryResponse {
    private BigDecimal totalPaidOut;
    private String currency;
    private Long transactionCount;
    private LocalDateTime firstPayoutDate;
    private LocalDateTime lastPayoutDate;
    private List<PayoutTransactionResponse> recentTransactions;
}
