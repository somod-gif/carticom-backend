package com.carticom.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/whatsapp")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Commerce", description = "WhatsApp order integration")
public class WhatsAppController {

    @GetMapping("/order-link")
    @Operation(summary = "Generate WhatsApp order link", description = "Creates a WhatsApp deep link for ordering")
    public ResponseEntity<Map<String, String>> generateOrderLink(
            Authentication authentication,
            @RequestParam String storeName,
            @RequestParam String productName,
            @RequestParam(defaultValue = "1") Integer quantity) {

        String message = String.format(
                "Hi %s! I'd like to order %s (x%d). Please confirm availability and total cost.",
                storeName, productName, quantity);

        String encodedMessage = java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
        String whatsappUrl = "https://wa.me/?text=" + encodedMessage;

        return ResponseEntity.ok(Map.of(
                "url", whatsappUrl,
                "message", message
        ));
    }
}
