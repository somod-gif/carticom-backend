package com.carticom.controller;

import com.carticom.dto.payment.PaymentInitRequest;
import com.carticom.dto.payment.PaymentInitResponse;
import com.carticom.dto.payment.PaymentResponse;
import com.carticom.dto.payment.PaymentVerifyResponse;
import com.carticom.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Seller/staff payment operations, Paystack & Nomba")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "List store payments", description = "All payments for the caller's store (vendor/staff)")
    public ResponseEntity<List<PaymentResponse>> listPayments(Authentication authentication) {
        return ResponseEntity.ok(paymentService.listPayments(authentication.getName()));
    }

    @PostMapping("/initialize")
    @Operation(summary = "Initialize payment for an order", description = "Starts a hosted checkout (Paystack, Nomba or Flutterwave) for an order")
    public ResponseEntity<PaymentInitResponse> initializePayment(
            Authentication authentication,
            @Valid @RequestBody PaymentInitRequest request) {
        if (request.getOrderId() == null) {
            throw new com.carticom.exception.BadRequestException("orderId is required");
        }
        return ResponseEntity.ok(paymentService.initializeForOrder(
                request.getOrderId(), request.getProvider(), request.getEmail(), request.getCallbackUrl()));
    }

    @GetMapping("/verify/{reference}")
    @Operation(summary = "Verify payment", description = "Verifies with the provider and settles the order (store-scoped)")
    public ResponseEntity<PaymentVerifyResponse> verifyPayment(
            Authentication authentication,
            @PathVariable String reference) {
        return ResponseEntity.ok(paymentService.verifyForSeller(authentication.getName(), reference));
    }
}
