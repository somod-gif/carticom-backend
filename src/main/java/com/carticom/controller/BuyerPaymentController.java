package com.carticom.controller;

import com.carticom.dto.payment.PaymentInitRequest;
import com.carticom.dto.payment.PaymentInitResponse;
import com.carticom.dto.payment.PaymentVerifyResponse;
import com.carticom.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/buyer/payments")
@RequiredArgsConstructor
@Tag(name = "Buyer Payments", description = "Public checkout: initialize and verify payments (guest checkout supported)")
public class BuyerPaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initialize")
    @Operation(summary = "Initialize payment", description = "Starts hosted checkout for an order — works for guests")
    public ResponseEntity<PaymentInitResponse> initialize(@Valid @RequestBody PaymentInitRequest request) {
        if (request.getOrderId() == null) {
            throw new com.carticom.exception.BadRequestException("orderId is required");
        }
        return ResponseEntity.ok(paymentService.initializeForOrder(
                request.getOrderId(), request.getProvider(), request.getEmail(), request.getCallbackUrl()));
    }

    @GetMapping("/verify/{reference}")
    @Operation(summary = "Verify payment", description = "Verifies a payment with the provider and marks the order paid")
    public ResponseEntity<PaymentVerifyResponse> verify(@PathVariable String reference) {
        return ResponseEntity.ok(paymentService.verifyAndSettle(reference));
    }
}
