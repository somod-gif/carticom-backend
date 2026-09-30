package com.carticom.service;

import com.carticom.dto.ai.AiChatRequest;
import com.carticom.dto.ai.AiChatResponse;
import com.carticom.dto.ai.AiInsightsResponse;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Store;
import com.carticom.model.User;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiService {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final BusinessDataService businessDataService;
    private final InventoryIntelligenceService inventoryIntelligenceService;
    private final BusinessHealthService businessHealthService;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final PlanGuard planGuard;

    public AiService(
            @Value("${cencori.api-key}") String apiKey,
            @Value("${cencori.model}") String model,
            @Value("${cencori.base-url}") String baseUrl,
            BusinessDataService businessDataService,
            InventoryIntelligenceService inventoryIntelligenceService,
            BusinessHealthService businessHealthService,
            UserRepository userRepository,
            StoreRepository storeRepository,
            StoreAccessService storeAccessService,
            PlanGuard planGuard) {
        this.apiKey = apiKey;
        this.model = model;
        this.businessDataService = businessDataService;
        this.inventoryIntelligenceService = inventoryIntelligenceService;
        this.businessHealthService = businessHealthService;
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.storeAccessService = storeAccessService;
        this.planGuard = planGuard;
        this.objectMapper = new ObjectMapper();
        WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);
        if (apiKey != null && !apiKey.isBlank()) {
            builder = builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        this.webClient = builder.build();
    }

    public AiChatResponse chat(String sellerEmail, AiChatRequest request) {
        planGuard.requireFeature(sellerEmail, "ai");
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("AI service not configured. Set CENCORI_API_KEY.");
        }

        Store store = getStoreBySeller(sellerEmail);
        Map<String, Object> context = businessDataService.getFullBusinessContext(store.getId());
        Map<String, Object> health = businessHealthService.computeHealthScore(store.getId());

        String systemPrompt = buildSystemPrompt(context, health);
        String userMessage = request.getMessage();

        if (request.getContext() != null && !request.getContext().isBlank()) {
            userMessage = "Context: " + request.getContext() + "\n\nQuestion: " + request.getMessage();
        }

        try {
            String responseText = callCencori(systemPrompt, userMessage);
            String suggestion = extractSuggestion(responseText);

            return AiChatResponse.builder()
                    .reply(responseText)
                    .suggestion(suggestion)
                    .build();
        } catch (WebClientResponseException e) {
            log.error("Cencori API error: {}", e.getMessage());
            throw new BadRequestException("AI service error: " + e.getMessage());
        }
    }

    public AiInsightsResponse getInsights(String sellerEmail) {
        planGuard.requireFeature(sellerEmail, "ai");
        if (apiKey == null || apiKey.isBlank()) {
            return AiInsightsResponse.builder()
                    .salesSummary("AI not configured. Set CENCORI_API_KEY environment variable.")
                    .recommendations(List.of(
                            "Configure Cencori API key to enable AI insights",
                            "Add CENCORI_API_KEY to your environment variables"
                    ))
                    .inventoryAlert("Connect your database to get inventory alerts")
                    .growthTip("Enable AI to get personalized growth tips")
                    .build();
        }

        try {
            Store store = getStoreBySeller(sellerEmail);
            Map<String, Object> context = businessDataService.getFullBusinessContext(store.getId());

            String prompt = String.format(
                    """
                    You are Carticom AI, a business advisor for African SMEs.
                    
                    Current business data:
                    - Total Revenue: ₦%s
                    - Total Orders: %s
                    - Total Customers: %s
                    - Total Products: %s
                    - Average Order Value: ₦%s
                    - Low Stock Products: %s
                    - Delivery Rate: %s%%
                    - Repeat Customer Rate: %s%%
                    - Cancellation Rate: %s%%
                    
                    Provide a brief sales summary (1-2 sentences), 3 actionable recommendations,
                    an inventory alert if relevant, and a growth tip. Be concise and specific.
                    Format as JSON with keys: salesSummary, recommendations (array), inventoryAlert, growthTip
                    """,
                    context.get("totalRevenue"),
                    context.get("totalOrders"),
                    context.get("totalCustomers"),
                    context.get("totalProducts"),
                    context.get("averageOrderValue"),
                    context.get("lowStockProducts"),
                    context.get("deliveryRate"),
                    context.get("repeatCustomerRate"),
                    context.get("cancellationRate")
            );

            String responseText = callCencori("You are a JSON-generating business advisor. Always respond with valid JSON only.", prompt);

            String cleaned = responseText.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode json = objectMapper.readTree(cleaned);

            return AiInsightsResponse.builder()
                    .salesSummary(json.path("salesSummary").asText("No summary available"))
                    .recommendations(objectMapper.convertValue(json.path("recommendations"), List.class))
                    .inventoryAlert(json.path("inventoryAlert").asText("No alerts"))
                    .growthTip(json.path("growthTip").asText("Keep growing!"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to generate insights: {}", e.getMessage());
            return AiInsightsResponse.builder()
                    .salesSummary("Unable to generate insights at this time")
                    .recommendations(List.of("Try again later", "Ensure your store has data"))
                    .inventoryAlert("Check your inventory manually")
                    .growthTip("Focus on customer retention")
                    .build();
        }
    }

    private String buildSystemPrompt(Map<String, Object> context, Map<String, Object> health) {
        return String.format("""
                You are Carticom AI, an intelligent business advisor built specifically for African SMEs.
                
                CURRENT BUSINESS STATE:
                - Revenue: ₦%s | Orders: %s | Customers: %s | Products: %s
                - Avg Order Value: ₦%s | Low Stock: %s products
                - Delivery Rate: %s%% | Repeat Customer Rate: %s%%
                - Health Score: %s/100
                
                You help merchants with:
                - Sales strategy and pricing optimization
                - Inventory management and restocking decisions
                - Customer retention and marketing tips
                - African market insights (Nigeria, Ghana, Kenya, etc.)
                - Payment and delivery best practices
                
                Be concise, actionable, and culturally aware. Use Naira (₦) for currency.
                Reference their actual data in your responses. Keep responses under 200 words unless asked for detail.
                """,
                context.get("totalRevenue"),
                context.get("totalOrders"),
                context.get("totalCustomers"),
                context.get("totalProducts"),
                context.get("averageOrderValue"),
                context.get("lowStockProducts"),
                context.get("deliveryRate"),
                context.get("repeatCustomerRate"),
                health.get("overallScore")
        );
    }

    private String callCencori(String systemPrompt, String userMessage) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                ),
                "temperature", 0.7,
                "max_tokens", 1024
        );

        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::extractTextFromResponse)
                .block();
    }

    private String extractTextFromResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            return root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText("No response generated");
        } catch (Exception e) {
            log.error("Failed to parse Cencori response: {}", e.getMessage());
            return "Failed to parse AI response";
        }
    }

    private String extractSuggestion(String response) {
        if (response.contains("ACTION:") || response.contains("Try:") || response.contains("Recommendation:")) {
            String[] parts = response.split("(ACTION:|Try:|Recommendation:)");
            if (parts.length > 1) {
                return parts[1].trim().split("\n")[0].trim();
            }
        }
        String[] sentences = response.split("\\.");
        return sentences.length > 1 ? sentences[1].trim() + "." : sentences[0].trim();
    }

    public AiChatResponse generateProductDescription(String sellerEmail, String input) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("AI service not configured. Set CENCORI_API_KEY.");
        }

        String prompt = String.format("""
                You are a product copywriter for an African e-commerce store.
                The seller typed: "%s"
                
                Generate a compelling product listing with:
                1. A catchy product title
                2. A detailed description (3-5 sentences) highlighting features, materials, and use cases
                3. Suggested price range in Naira (₦) for the Nigerian market
                4. 3-5 relevant tags/keywords
                
                Be culturally aware — reference Nigerian fashion, lifestyle, or market context where relevant.
                Use Naira (₦) for all prices.
                Format as a clean product listing, not JSON.
                """, input);

        try {
            String responseText = callCencori(
                    "You are an expert African e-commerce product copywriter. Be concise and compelling.",
                    prompt
            );
            return AiChatResponse.builder()
                    .reply(responseText)
                    .suggestion("Use this description in your product listing")
                    .build();
        } catch (WebClientResponseException e) {
            log.error("Cencori API error for description: {}", e.getMessage());
            throw new BadRequestException("AI service error: " + e.getMessage());
        }
    }

    private Store getStoreBySeller(String sellerEmail) {
        return storeAccessService.resolveStore(sellerEmail);
    }
}
