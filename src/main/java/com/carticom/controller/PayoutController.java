package com.carticom.controller;

import com.carticom.dto.common.SuccessResponse;
import com.carticom.dto.payout.PayoutSummaryResponse;
import com.carticom.service.PayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
@Tag(name = "Payouts", description = "Seller payout and settlement endpoints")
public class PayoutController {

    private final PayoutService payoutService;

    @GetMapping("/{id}/payouts")
    @Operation(summary = "Get payout summary",
            description = "Returns total paid out, transaction count, payout date range and "
                    + "recent transactions for a store, aggregated from completed (PAID) payments")
    @ApiResponse(responseCode = "200", description = "Payout summary returned")
    @ApiResponse(responseCode = "404", description = "Store not found or not owned by caller")
    public ResponseEntity<SuccessResponse<PayoutSummaryResponse>> getPayoutSummary(
            Authentication authentication,
            @PathVariable Long id) {
        PayoutSummaryResponse summary = payoutService.getPayoutSummary(authentication.getName(), id);
        return ResponseEntity.ok(new SuccessResponse<>(true, summary));
    }
}
