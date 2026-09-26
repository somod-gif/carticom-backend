package com.carticom.service;

import com.carticom.dto.payment.PaymentInitResponse;
import com.carticom.dto.payment.PaymentResponse;
import com.carticom.dto.payment.PaymentVerifyResponse;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ForbiddenException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Order;
import com.carticom.model.Payment;
import com.carticom.model.PaymentMethod;
import com.carticom.model.PaymentStatus;
import com.carticom.model.Store;
import com.carticom.repository.OrderRepository;
import com.carticom.repository.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final StoreAccessService storeAccessService;
    private final SendByteService sendByteService;
    private final ObjectMapper objectMapper;

    private final WebClient paystackClient;
    private final WebClient flutterwaveClient;
    private final String paystackSecretKey;
    private final String nombaBaseUrl;
    private final String nombaClientId;
    private final String nombaClientSecret;
    private final String nombaAccountId;
    private final String appBaseUrl;

    private volatile String nombaAccessToken;
    private volatile String nombaRefreshToken;
    private volatile Instant nombaTokenExpiresAt = Instant.EPOCH;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            StoreAccessService storeAccessService,
            SendByteService sendByteService,
            @Value("${paystack.secret-key}") String paystackKey,
            @Value("${paystack.base-url}") String paystackUrl,
            @Value("${flutterwave.secret-key}") String flutterwaveKey,
            @Value("${flutterwave.base-url}") String flutterwaveUrl,
            @Value("${nomba.client-id}") String nombaClientId,
            @Value("${nomba.client-secret}") String nombaClientSecret,
            @Value("${nomba.account-id}") String nombaAccountId,
            @Value("${nomba.base-url}") String nombaBaseUrl,
            @Value("${app.base-url}") String appBaseUrl) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.storeAccessService = storeAccessService;
        this.sendByteService = sendByteService;
        this.objectMapper = new ObjectMapper();
        this.paystackSecretKey = paystackKey;
        this.nombaClientId = nombaClientId;
        this.nombaClientSecret = nombaClientSecret;
        this.nombaAccountId = nombaAccountId;
        this.nombaBaseUrl = nombaBaseUrl;
        this.appBaseUrl = appBaseUrl;

        this.paystackClient = WebClient.builder()
                .baseUrl(paystackUrl)
                .defaultHeader("Authorization", "Bearer " + paystackKey)
                .build();

        this.flutterwaveClient = WebClient.builder()
                .baseUrl(flutterwaveUrl)
                .defaultHeader("Authorization", "Bearer " + flutterwaveKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    private record ProviderResult(boolean verified, boolean terminal, String rawStatus,
                                  BigDecimal amount, String message) {}

    // ===== Initialize =====

    @Transactional
    public PaymentInitResponse initializeForOrder(Long orderId, String provider, String email, String callbackUrl) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BadRequestException("Order is already paid");
        }

        String p = provider.toLowerCase();
        if (!List.of("paystack", "nomba", "flutterwave").contains(p)) {
            throw new BadRequestException("Unsupported provider: " + provider);
        }

        String reference = "CART-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Payment payment = Payment.builder()
                .order(order)
                .method(PaymentMethod.valueOf(p.toUpperCase()))
                .status(PaymentStatus.PENDING)
                .amount(order.getTotal())
                .reference(reference)
                .build();
        paymentRepository.save(payment);

        order.setPaymentStatus(PaymentStatus.PENDING);
        orderRepository.save(order);

        String buyerEmail = (order.getCustomer() != null && order.getCustomer().getEmail() != null)
                ? order.getCustomer().getEmail()
                : email;
        String callback = (callbackUrl == null || callbackUrl.isBlank())
                ? appBaseUrl + "/checkout/callback"
                : callbackUrl;

        try {
            PaymentInitResponse response = switch (p) {
                case "paystack" -> initPaystack(payment, order, reference, buyerEmail, callback);
                case "nomba" -> initNomba(payment, order, reference, buyerEmail, callback);
                default -> initFlutterwave(payment, order, reference, buyerEmail, callback);
            };
            paymentRepository.save(payment);
            log.info("Payment initialized: ref={} provider={} order={}", reference, p, order.getOrderNumber());
            return response;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Payment init failed for ref={}: {}", reference, e.getMessage());
            throw new BadRequestException("Payment initialization failed: " + e.getMessage());
        }
    }

    private PaymentInitResponse initPaystack(Payment payment, Order order, String reference,
                                             String buyerEmail, String callbackUrl) throws Exception {
        if (paystackSecretKey == null || paystackSecretKey.isBlank()) {
            throw new BadRequestException("Paystack is not configured (PAYSTACK_SECRET_KEY missing)");
        }
        Map<String, Object> body = Map.of(
                "amount", order.getTotal().multiply(BigDecimal.valueOf(100)).longValue(),
                "currency", "NGN",
                "email", buyerEmail,
                "reference", reference,
                "callback_url", callbackUrl
        );

        String response = paystackClient.post()
                .uri("/transaction/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode data = objectMapper.readTree(response).path("data");
        return PaymentInitResponse.builder()
                .reference(reference)
                .authorizationUrl(data.path("authorization_url").asText())
                .accessCode(data.path("access_code").asText())
                .provider("paystack")
                .amount(order.getTotal())
                .status("pending")
                .build();
    }

    private PaymentInitResponse initNomba(Payment payment, Order order, String reference,
                                          String buyerEmail, String callbackUrl) throws Exception {
        String token = getNombaAccessToken();

        Map<String, Object> body = Map.of(
                "order", Map.of(
                        "amount", order.getTotal().toPlainString(),
                        "currency", "NGN",
                        "orderReference", reference,
                        "callbackUrl", callbackUrl,
                        "customerEmail", buyerEmail
                )
        );

        String response = WebClient.builder()
                .baseUrl(nombaBaseUrl)
                .build()
                .post()
                .uri("/checkout/order")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .header("accountId", nombaAccountId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode json = objectMapper.readTree(response);
        if (!"00".equals(json.path("code").asText())) {
            throw new BadRequestException("Nomba checkout failed: " + json.path("description").asText());
        }
        JsonNode data = json.path("data");
        payment.setGatewayReference(data.path("orderReference").asText(null));

        return PaymentInitResponse.builder()
                .reference(reference)
                .authorizationUrl(data.path("checkoutLink").asText())
                .provider("nomba")
                .amount(order.getTotal())
                .status("pending")
                .build();
    }

    private PaymentInitResponse initFlutterwave(Payment payment, Order order, String reference,
                                                String buyerEmail, String callbackUrl) {
        Map<String, Object> body = Map.of(
                "amount", order.getTotal().toPlainString(),
                "currency", "NGN",
                "email", buyerEmail,
                "tx_ref", reference,
                "redirect_url", callbackUrl
        );

        String response = flutterwaveClient.post()
                .uri("/payments")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode data;
        try {
            data = objectMapper.readTree(response).path("data");
        } catch (Exception e) {
            throw new BadRequestException("Flutterwave payment initialization failed");
        }

        return PaymentInitResponse.builder()
                .reference(reference)
                .authorizationUrl(data.path("link").asText())
                .provider("flutterwave")
                .amount(order.getTotal())
                .status("pending")
                .build();
    }

    // ===== Verify & settle =====

    @Transactional
    public PaymentVerifyResponse verifyAndSettle(String reference) {
        Payment payment = findPayment(reference);
        if (payment.getStatus() == PaymentStatus.PAID) {
            return buildResponse(payment, "SUCCESS", true);
        }

        ProviderResult result = switch (payment.getMethod()) {
            case PAYSTACK -> verifyPaystack(payment.getReference());
            case NOMBA -> verifyNomba(payment.getReference());
            case FLUTTERWAVE -> verifyFlutterwave(payment.getReference());
            case CARD, MOBILE_MONEY, BANK_TRANSFER, DVA, CASH_ON_DELIVERY ->
                    throw new BadRequestException("Unsupported payment method: " + payment.getMethod());
        };
        return settle(payment, result);
    }

    private PaymentVerifyResponse settle(Payment payment, ProviderResult result) {
        if (payment.getStatus() == PaymentStatus.PAID) {
            return buildResponse(payment, "SUCCESS", true);
        }
        Order order = payment.getOrder();

        if (result.verified()) {
            BigDecimal expected = order.getTotal();
            if (result.amount() == null || result.amount().compareTo(expected) != 0) {
                log.error("PAYMENT AMOUNT MISMATCH ref={} expected={} providerAmount={} - marking FAILED",
                        payment.getReference(), expected, result.amount());
                payment.setStatus(PaymentStatus.FAILED);
                payment.setGatewayResponse("Amount mismatch: expected " + expected + ", got " + result.amount());
                order.setPaymentStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                orderRepository.save(order);
                return buildResponse(payment, "AMOUNT_MISMATCH", false);
            }

            payment.setStatus(PaymentStatus.PAID);
            payment.setGatewayResponse(result.message());
            order.setPaymentStatus(PaymentStatus.PAID);
            paymentRepository.save(payment);
            orderRepository.save(order);
            sendReceipts(order);
            log.info("Payment settled: ref={} order={} amount={}",
                    payment.getReference(), order.getOrderNumber(), expected);
            return buildResponse(payment, "SUCCESS", true);
        }

        if (result.terminal()) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setGatewayResponse(result.message());
            order.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            orderRepository.save(order);
        }
        return buildResponse(payment, result.rawStatus(), false);
    }

    private void sendReceipts(Order order) {
        try {
            String buyerEmail = order.getCustomer() != null ? order.getCustomer().getEmail() : null;
            String sellerEmail = order.getStore() != null ? order.getStore().getSeller().getEmail() : null;
            String storeName = order.getStore() != null ? order.getStore().getName() : "";
            sendByteService.sendPaymentReceipt(buyerEmail, sellerEmail, order.getOrderNumber(),
                    order.getTotal().toPlainString(), storeName);
        } catch (Exception e) {
            log.warn("Failed to send payment receipts: {}", e.getMessage());
        }
    }

    private ProviderResult verifyPaystack(String reference) {
        if (paystackSecretKey == null || paystackSecretKey.isBlank()) {
            throw new BadRequestException("Paystack is not configured");
        }
        try {
            String response = paystackClient.get()
                    .uri("/transaction/verify/{reference}", reference)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode data = objectMapper.readTree(response).path("data");
            String status = data.path("status").asText("");
            BigDecimal amount = data.path("amount").isMissingNode()
                    ? null
                    : BigDecimal.valueOf(data.path("amount").asLong()).divide(BigDecimal.valueOf(100));
            String message = data.path("gateway_response").asText("");

            boolean verified = "success".equals(status);
            boolean terminal = "failed".equals(status) || "abandoned".equals(status) || "reversed".equals(status);
            return new ProviderResult(verified, terminal, status.toUpperCase(), amount, message);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return new ProviderResult(false, false, "NOT_FOUND", null, "Reference not found at Paystack");
            }
            throw new BadRequestException("Paystack verification failed");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Paystack verification failed: " + e.getMessage());
        }
    }

    private ProviderResult verifyNomba(String reference) {
        String token = getNombaAccessToken();
        try {
            String response = WebClient.builder()
                    .baseUrl(nombaBaseUrl)
                    .build()
                    .get()
                    .uri(uri -> uri.path("/transactions/accounts/single")
                            .queryParam("orderReference", reference)
                            .build())
                    .header("Authorization", "Bearer " + token)
                    .header("accountId", nombaAccountId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode json = objectMapper.readTree(response);
            if (!"00".equals(json.path("code").asText())) {
                return new ProviderResult(false, false, "NOT_FOUND", null, "Transaction not found at Nomba");
            }
            JsonNode data = json.path("data");
            String status = data.path("status").asText("");
            BigDecimal amount = data.has("amount")
                    ? new BigDecimal(data.path("amount").asText())
                    : (data.has("onlineCheckoutAmount") ? new BigDecimal(data.path("onlineCheckoutAmount").asText()) : null);
            String message = data.path("gatewayMessage").asText("");

            boolean verified = "SUCCESS".equalsIgnoreCase(status);
            boolean terminal = "FAILED".equalsIgnoreCase(status) || "REVERSED".equalsIgnoreCase(status);
            return new ProviderResult(verified, terminal, status, amount, message);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Nomba verification failed: " + e.getMessage());
        }
    }

    private ProviderResult verifyFlutterwave(String reference) {
        try {
            String response = flutterwaveClient.get()
                    .uri("/transactions/verify_by_reference?tx_ref={reference}", reference)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode data = objectMapper.readTree(response).path("data");
            String status = data.path("status").asText("");
            BigDecimal amount = data.has("amount") ? new BigDecimal(data.path("amount").asText()) : null;
            String message = data.path("gateway_response").asText("");

            boolean verified = "successful".equals(status);
            boolean terminal = "failed".equals(status);
            return new ProviderResult(verified, terminal, status.toUpperCase(), amount, message);
        } catch (Exception e) {
            throw new BadRequestException("Flutterwave verification failed: " + e.getMessage());
        }
    }

    // ===== Seller-facing =====

    public PaymentVerifyResponse verifyForSeller(String email, String reference) {
        Payment payment = findPayment(reference);
        Store store = storeAccessService.resolveStore(email);
        if (!payment.getOrder().getStore().getId().equals(store.getId())) {
            throw new ForbiddenException("This payment belongs to another store");
        }
        return verifyAndSettle(reference);
    }

    public List<PaymentResponse> listPayments(String email) {
        Store store = storeAccessService.resolveStore(email);
        return paymentRepository.findByOrderStoreId(store.getId()).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::mapToListResponse)
                .toList();
    }

    // ===== Webhook support =====

    public boolean settleIfKnown(String reference) {
        try {
            findPayment(reference);
        } catch (ResourceNotFoundException e) {
            log.warn("Webhook reference not recognized: {}", reference);
            return false;
        }
        verifyAndSettle(reference);
        return true;
    }

    public Payment findPayment(String reference) {
        return paymentRepository.findByReference(reference)
                .or(() -> paymentRepository.findByGatewayReference(reference))
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    // ===== Nomba auth =====

    private synchronized String getNombaAccessToken() {
        if (nombaAccessToken != null && Instant.now().plusSeconds(300).isBefore(nombaTokenExpiresAt)) {
            return nombaAccessToken;
        }
        if (nombaRefreshToken != null && !nombaRefreshToken.isBlank()) {
            try {
                return refreshNombaToken();
            } catch (Exception e) {
                log.warn("Nomba token refresh failed, issuing new token: {}", e.getMessage());
            }
        }
        return issueNombaToken();
    }

    private String issueNombaToken() {
        if (nombaClientId == null || nombaClientId.isBlank()) {
            throw new BadRequestException("Nomba is not configured (NOMBA_CLIENT_ID missing)");
        }
        String response = WebClient.builder()
                .baseUrl(nombaBaseUrl)
                .build()
                .post()
                .uri("/auth/token/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .header("accountId", nombaAccountId)
                .bodyValue(Map.of(
                        "grant_type", "client_credentials",
                        "client_id", nombaClientId,
                        "client_secret", nombaClientSecret
                ))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode json;
        try {
            json = objectMapper.readTree(response);
        } catch (Exception e) {
            throw new BadRequestException("Nomba auth failed");
        }
        if (!"00".equals(json.path("code").asText())) {
            throw new BadRequestException("Nomba auth failed: " + json.path("description").asText());
        }
        cacheNombaToken(json.path("data"));
        log.info("Nomba access token issued, expires {}", nombaTokenExpiresAt);
        return nombaAccessToken;
    }

    private String refreshNombaToken() {
        String response = WebClient.builder()
                .baseUrl(nombaBaseUrl)
                .build()
                .post()
                .uri("/auth/token/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + nombaAccessToken)
                .header("accountId", nombaAccountId)
                .bodyValue(Map.of(
                        "grant_type", "refresh_token",
                        "refresh_token", nombaRefreshToken
                ))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode json;
        try {
            json = objectMapper.readTree(response);
        } catch (Exception e) {
            throw new BadRequestException("Nomba auth failed");
        }
        if (!"00".equals(json.path("code").asText())) {
            throw new BadRequestException("Nomba token refresh rejected");
        }
        cacheNombaToken(json.path("data"));
        return nombaAccessToken;
    }

    private void cacheNombaToken(JsonNode data) {
        nombaAccessToken = data.path("access_token").asText(null);
        String refresh = data.path("refresh_token").asText(null);
        if (refresh != null && !refresh.isBlank()) {
            nombaRefreshToken = refresh;
        }
        Instant expiresAt;
        try {
            expiresAt = Instant.parse(data.path("expiresAt").asText());
        } catch (Exception e) {
            expiresAt = Instant.now().plusSeconds(1500);
        }
        nombaTokenExpiresAt = expiresAt;
    }

    // ===== Mapping =====

    private PaymentResponse mapToListResponse(Payment payment) {
        return PaymentResponse.builder()
                .reference(payment.getReference())
                .amount(payment.getAmount())
                .method(payment.getMethod().name())
                .status(payment.getStatus() == PaymentStatus.PAID ? "SUCCESS" : payment.getStatus().name())
                .orderNumber(payment.getOrder() != null ? payment.getOrder().getOrderNumber() : null)
                .gatewayResponse(payment.getGatewayResponse())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private PaymentVerifyResponse buildResponse(Payment payment, String status, boolean verified) {
        return PaymentVerifyResponse.builder()
                .reference(payment.getReference())
                .status(status)
                .amount(payment.getAmount())
                .provider(payment.getMethod().name().toLowerCase())
                .gatewayResponse(payment.getGatewayResponse())
                .verified(verified)
                .build();
    }
}
