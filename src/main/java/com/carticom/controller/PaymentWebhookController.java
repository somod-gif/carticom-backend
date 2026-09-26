package com.carticom.controller;

import com.carticom.exception.ResourceNotFoundException;
import com.carticom.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${paystack.secret-key}")
    private String paystackSecretKey;

    @Value("${nomba.webhook-signature-key}")
    private String nombaWebhookSignatureKey;

    @PostMapping("/paystack")
    public ResponseEntity<String> paystackWebhook(
            @RequestBody String body,
            @RequestHeader(value = "x-paystack-signature", required = false) String signature) {

        if (paystackSecretKey == null || paystackSecretKey.isBlank()) {
            log.warn("Paystack webhook ignored: PAYSTACK_SECRET_KEY not configured");
            return ResponseEntity.ok("ignored");
        }
        if (signature == null || !signatureEquals(paystackSignature(body, paystackSecretKey), signature)) {
            log.warn("Paystack webhook rejected: invalid signature");
            return ResponseEntity.status(401).body("invalid signature");
        }

        try {
            JsonNode json = objectMapper.readTree(body);
            String reference = json.path("data").path("reference").asText(null);
            if (reference == null || reference.isBlank()) {
                return ResponseEntity.ok("no reference");
            }
            paymentService.settleIfKnown(reference);
            return ResponseEntity.ok("ok");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.ok("unknown reference");
        } catch (Exception e) {
            log.error("Paystack webhook processing failed: {}", e.getMessage());
            return ResponseEntity.status(500).body("error");
        }
    }

    @PostMapping("/nomba")
    public ResponseEntity<String> nombaWebhook(
            @RequestBody String body,
            @RequestHeader(value = "nomba-signature", required = false) String signature,
            @RequestHeader(value = "nomba-timestamp", required = false) String timestamp) {

        if (nombaWebhookSignatureKey == null || nombaWebhookSignatureKey.isBlank()) {
            log.warn("Nomba webhook ignored: NOMBA_WEBHOOK_SIGNATURE_KEY not configured");
            return ResponseEntity.ok("ignored");
        }
        if (signature == null || timestamp == null) {
            log.warn("Nomba webhook rejected: missing signature headers");
            return ResponseEntity.status(401).body("invalid signature");
        }

        try {
            JsonNode json = objectMapper.readTree(body);
            String computed = nombaSignature(json, timestamp, nombaWebhookSignatureKey);
            if (!signatureEquals(computed, signature)) {
                log.warn("Nomba webhook rejected: signature mismatch");
                return ResponseEntity.status(401).body("invalid signature");
            }

            String eventType = json.path("event_type").asText("");
            if (!eventType.startsWith("payment_")) {
                return ResponseEntity.ok("event ignored");
            }

            JsonNode order = json.path("data").path("order");
            String reference = firstNonBlank(order.path("orderReference").asText(null),
                    order.path("orderId").asText(null));
            if (reference == null) {
                return ResponseEntity.ok("no reference");
            }
            paymentService.settleIfKnown(reference);
            return ResponseEntity.ok("ok");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.ok("unknown reference");
        } catch (Exception e) {
            log.error("Nomba webhook processing failed: {}", e.getMessage());
            return ResponseEntity.status(500).body("error");
        }
    }

    private String paystackSignature(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute Paystack signature", e);
        }
    }

    private String nombaSignature(JsonNode json, String timestamp, String secret) {
        JsonNode data = json.path("data");
        JsonNode merchant = data.path("merchant");
        JsonNode transaction = data.path("transaction");

        String requestId = json.path("requestId").asText(null);
        if (requestId == null) {
            requestId = json.path("request_id").asText("");
        }
        String responseCode = transaction.path("responseCode").asText("");
        if ("null".equalsIgnoreCase(responseCode)) {
            responseCode = "";
        }

        String payload = String.join(":",
                json.path("event_type").asText(""),
                requestId,
                merchant.path("userId").asText(""),
                merchant.path("walletId").asText(""),
                transaction.path("transactionId").asText(""),
                transaction.path("type").asText(""),
                transaction.path("time").asText(""),
                responseCode,
                timestamp
        );

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute Nomba signature", e);
        }
    }

    private boolean signatureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
