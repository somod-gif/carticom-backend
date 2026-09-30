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
    private final com.carticom.repository.PaymentRepository paymentRepository;

    @GetMapping("/order/{orderId}")
    public ResponseEntity<java.util.Map<String, Object>> getPaymentForOrder(@PathVariable Long orderId) {
        return paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .map(p -> ResponseEntity.ok(mapPayment(p)))
                .orElseGet(() -> ResponseEntity.ok(new java.util.LinkedHashMap<>()));
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<java.util.Map<String, Object>>> getPaymentsForStore(@PathVariable Long storeId) {
        return ResponseEntity.ok(paymentRepository.findByOrderStoreId(storeId).stream()
                .map(this::mapPayment)
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<java.util.Map<String, Object>> getPaymentById(@PathVariable Long id) {
        return paymentRepository.findById(id)
                .map(p -> ResponseEntity.ok(mapPayment(p)))
                .orElseThrow(() -> new com.carticom.exception.ResourceNotFoundException("Payment not found"));
    }

    private java.util.Map<String, Object> mapPayment(com.carticom.model.Payment p) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("storeId", p.getOrder() != null && p.getOrder().getStore() != null
                ? p.getOrder().getStore().getId() : null);
        m.put("orderId", p.getOrder() != null ? p.getOrder().getId() : null);
        m.put("transactionId", p.getReference());
        m.put("providerReference", p.getGatewayReference());
        m.put("status", mapPaymentStatus(p.getStatus()));
        m.put("amount", p.getAmount());
        m.put("currency", "NGN");
        m.put("paymentMethod", p.getMethod() != null ? p.getMethod().name() : null);
        m.put("paymentProvider", p.getMethod() != null ? p.getMethod().name() : null);
        m.put("message", p.getGatewayResponse());
        m.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        m.put("updatedAt", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
        return m;
    }

    private String mapPaymentStatus(com.carticom.model.PaymentStatus status) {
        if (status == null) return "PENDING";
        return switch (status) {
            case PAID -> "COMPLETED";
            case PARTIALLY_REFUNDED -> "REFUNDED";
            default -> status.name();
        };
    }

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

    public record InitiateRequest(Long orderId, String paymentMethod, String paymentProvider,
                                  String email, String callbackUrl) {}

    public record ConfirmRequest(String transactionId, Long orderId, String status, String providerReference) {}

    @PostMapping("/initiate")
    public ResponseEntity<PaymentInitResponse> initiate(
            @RequestBody InitiateRequest request) {
        if (request.orderId() == null) {
            throw new com.carticom.exception.BadRequestException("orderId is required");
        }
        String provider = request.paymentProvider() != null && !request.paymentProvider().isBlank()
                ? request.paymentProvider() : "paystack";
        return ResponseEntity.ok(paymentService.initializeForOrder(
                request.orderId(), provider, request.email(), request.callbackUrl()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<PaymentVerifyResponse> confirm(@RequestBody ConfirmRequest request) {
        String reference = request.providerReference() != null && !request.providerReference().isBlank()
                ? request.providerReference()
                : request.transactionId();
        if (reference == null || reference.isBlank()) {
            throw new com.carticom.exception.BadRequestException("providerReference is required");
        }
        return ResponseEntity.ok(paymentService.verifyAndSettle(reference));
    }
}
