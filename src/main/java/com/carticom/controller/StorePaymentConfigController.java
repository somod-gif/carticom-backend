package com.carticom.controller;

import com.carticom.model.Store;
import com.carticom.model.StorePaymentConfig;
import com.carticom.repository.StorePaymentConfigRepository;
import com.carticom.service.StoreAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/payment-config")
@RequiredArgsConstructor
@Tag(name = "Store Payment Config", description = "Per-store payment provider credentials")
public class StorePaymentConfigController {

    private final StorePaymentConfigRepository paymentConfigRepository;
    private final StoreAccessService storeAccessService;

    public record CredentialsRequest(String paystackSecretKey, String paystackPublicKey,
                                     String flutterwaveSecretKey, String flutterwaveVerifyHash,
                                     String activeProvider) {}

    @GetMapping
    @Operation(summary = "Get payment provider status for a store")
    public ResponseEntity<Map<String, Object>> get(Authentication authentication,
                                                   @PathVariable Long storeId) {
        Store store = requireOwnStore(authentication.getName(), storeId);
        return ResponseEntity.ok(toResponse(
                paymentConfigRepository.findByStoreId(store.getId()).orElse(null), store));
    }

    @PutMapping("/credentials")
    @Operation(summary = "Save payment provider credentials",
            description = "Secrets are stored server-side and never returned")
    public ResponseEntity<Map<String, Object>> save(Authentication authentication,
                                                    @PathVariable Long storeId,
                                                    @RequestBody CredentialsRequest request) {
        Store store = requireOwnStore(authentication.getName(), storeId);
        StorePaymentConfig config = paymentConfigRepository.findByStoreId(store.getId())
                .orElseGet(() -> {
            StorePaymentConfig c = new StorePaymentConfig();
            c.setStore(store);
            return c;
        });

        if (request.paystackSecretKey() != null && !request.paystackSecretKey().isBlank()) {
            config.setPaystackSecretKey(request.paystackSecretKey().trim());
        }
        if (request.paystackPublicKey() != null && !request.paystackPublicKey().isBlank()) {
            config.setPaystackPublicKey(request.paystackPublicKey().trim());
        }
        if (request.flutterwaveSecretKey() != null && !request.flutterwaveSecretKey().isBlank()) {
            config.setFlutterwaveSecretKey(request.flutterwaveSecretKey().trim());
        }
        if (request.flutterwaveVerifyHash() != null && !request.flutterwaveVerifyHash().isBlank()) {
            config.setFlutterwaveVerifyHash(request.flutterwaveVerifyHash().trim());
        }
        if (request.activeProvider() != null && !request.activeProvider().isBlank()) {
            config.setActiveProvider(request.activeProvider().trim().toUpperCase());
        }
        if (config.getConnectedAt() == null) {
            config.setConnectedAt(LocalDateTime.now());
        }
        paymentConfigRepository.save(config);
        return ResponseEntity.ok(toResponse(config, store));
    }

    private Store requireOwnStore(String email, Long storeId) {
        Store store = storeAccessService.resolveStore(email);
        if (!store.getId().equals(storeId)) {
            throw new com.carticom.exception.ResourceNotFoundException("Store not found");
        }
        return store;
    }

    private Map<String, Object> toResponse(StorePaymentConfig config, Store store) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("storeId", store.getId());
        boolean paystack = config != null && config.getPaystackSecretKey() != null;
        boolean flutterwave = config != null && config.getFlutterwaveSecretKey() != null;
        res.put("paystackConnected", paystack);
        res.put("flutterwaveConnected", flutterwave);
        res.put("activeProvider", config != null ? config.getActiveProvider() : null);
        res.put("paystackPublicKeyMasked",
                config != null ? mask(config.getPaystackPublicKey()) : null);
        res.put("virtualAccountNumber", config != null ? config.getVirtualAccountNumber() : null);
        res.put("virtualAccountName", config != null ? config.getVirtualAccountName() : null);
        res.put("virtualBankName", config != null ? config.getVirtualBankName() : null);
        res.put("virtualAccountProvider", config != null ? config.getVirtualAccountProvider() : null);
        res.put("connectedAt", config != null && config.getConnectedAt() != null
                ? config.getConnectedAt().toString() : null);
        return res;
    }

    private String mask(String key) {
        if (key == null || key.isBlank()) return null;
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
