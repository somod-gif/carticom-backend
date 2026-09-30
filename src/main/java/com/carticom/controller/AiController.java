package com.carticom.controller;

import com.carticom.dto.ai.AiChatRequest;
import com.carticom.dto.ai.AiChatResponse;
import com.carticom.dto.ai.AiInsightsResponse;
import com.carticom.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Business Advisor", description = "AI-powered business insights and chat powered by Gemini")
public class AiController {

    private final AiService aiService;

    @GetMapping
    @Operation(summary = "AI config (list)")
    public ResponseEntity<java.util.Map<String, Object>> aiConfig() {
        return ResponseEntity.ok(defaultAiConfig());
    }

    @GetMapping("/status/{storeId}")
    @Operation(summary = "AI status for store")
    public ResponseEntity<java.util.Map<String, Object>> aiStatus(@PathVariable Long storeId) {
        return ResponseEntity.ok(defaultAiConfig());
    }

    @PostMapping("/enable/{storeId}")
    @Operation(summary = "Enable/disable AI for store")
    public ResponseEntity<java.util.Map<String, Object>> aiEnable(
            @PathVariable Long storeId,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        java.util.Map<String, Object> config = defaultAiConfig();
        if (body != null && body.get("enabled") instanceof Boolean enabled) {
            config.put("enabled", enabled);
            config.put("status", enabled ? "ACTIVE" : "DISABLED");
        }
        return ResponseEntity.ok(config);
    }

    private java.util.Map<String, Object> defaultAiConfig() {
        java.util.Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("enabled", true);
        config.put("status", "ACTIVE");
        config.put("whatsappConnected", false);
        return config;
    }

    @PostMapping("/chat")
    @Operation(summary = "Chat with Carticom AI", description = "Ask business questions and get AI-powered advice")
    public ResponseEntity<AiChatResponse> chat(
            Authentication authentication,
            @Valid @RequestBody AiChatRequest request) {
        return ResponseEntity.ok(aiService.chat(authentication.getName(), request));
    }

    @GetMapping("/insights")
    @Operation(summary = "Get AI business insights", description = "Automated insights based on your store data")
    public ResponseEntity<AiInsightsResponse> getInsights(Authentication authentication) {
        return ResponseEntity.ok(aiService.getInsights(authentication.getName()));
    }

    @PostMapping("/generate-description")
    @Operation(summary = "Generate product description from short input", description = "Type 'ankara dress size 12 red' and get a full listing")
    public ResponseEntity<AiChatResponse> generateDescription(
            Authentication authentication,
            @RequestBody String input) {
        return ResponseEntity.ok(aiService.generateProductDescription(authentication.getName(), input));
    }
}
