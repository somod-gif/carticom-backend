package com.carticom.controller;

import com.carticom.dto.pos.PosCheckoutRequest;
import com.carticom.dto.pos.PosCheckoutResponse;
import com.carticom.model.PosTransaction;
import com.carticom.service.PosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pos")
@RequiredArgsConstructor
@Tag(name = "Point of Sale", description = "POS system with barcode scanning and receipts")
public class PosController {

    private final PosService posService;

    @PostMapping("/checkout")
    @Operation(summary = "POS Checkout", description = "Process a POS sale with cart items")
    public ResponseEntity<PosCheckoutResponse> checkout(
            Authentication authentication,
            @Valid @RequestBody PosCheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(posService.checkout(authentication.getName(), request));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get POS transactions")
    public ResponseEntity<List<PosTransaction>> getTransactions(Authentication authentication) {
        return ResponseEntity.ok(posService.getTransactions(authentication.getName()));
    }
}
