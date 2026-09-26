package com.carticom.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Slf4j
@Service
public class SendByteService {

    private final WebClient webClient;
    private final String apiKey;
    private final String fromEmail;

    public SendByteService(
            @Value("${sendbyte.api-key}") String apiKey,
            @Value("${sendbyte.base-url}") String baseUrl,
            @Value("${sendbyte.from-email}") String fromEmail) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public void sendOrderNotification(String sellerEmail, String orderNumber, String customerName, String total, String storeName) {
        if (sellerEmail == null || sellerEmail.isBlank()) {
            log.warn("Order notification skipped: seller has no email");
            return;
        }
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;padding:24px;border:1px solid #e2e8f0;border-radius:12px">
                  <h2 style="color:#2563eb;margin-top:0">New order received</h2>
                  <p><strong>%s</strong> just placed an order on <strong>%s</strong>.</p>
                  <table style="width:100%%;font-size:14px;color:#334155">
                    <tr><td style="padding:6px 0">Order</td><td style="text-align:right"><strong>%s</strong></td></tr>
                    <tr><td style="padding:6px 0">Customer</td><td style="text-align:right">%s</td></tr>
                    <tr><td style="padding:6px 0">Total</td><td style="text-align:right"><strong>N&#8358;%s</strong></td></tr>
                  </table>
                  <p style="margin-bottom:0"><a href="https://carticom.cv/dashboard/orders" style="background:#2563eb;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;display:inline-block">Open dashboard</a></p>
                </div>
                """.formatted(customerName, storeName, orderNumber, customerName, total);
        send(sellerEmail, "New order " + orderNumber + " - N" + total, html);
    }

    public void sendOrderStatusUpdate(String customerEmail, String orderNumber, String status) {
        if (customerEmail == null || customerEmail.isBlank()) {
            return;
        }
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;padding:24px;border:1px solid #e2e8f0;border-radius:12px">
                  <h2 style="color:#2563eb;margin-top:0">Order update</h2>
                  <p>Your order <strong>%s</strong> is now <strong>%s</strong>.</p>
                  <p style="color:#64748b;font-size:13px">Thank you for shopping with us.</p>
                </div>
                """.formatted(orderNumber, status);
        send(customerEmail, "Order " + orderNumber + " is " + status, html);
    }

    public void sendPaymentReceipt(String buyerEmail, String sellerEmail, String orderNumber,
                                   String amount, String storeName) {
        String buyerHtml = """
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;padding:24px;border:1px solid #e2e8f0;border-radius:12px">
                  <h2 style="color:#16a34a;margin-top:0">Payment confirmed</h2>
                  <p>We received your payment of <strong>N&#8358;%s</strong> for order <strong>%s</strong>.</p>
                  <p style="color:#64748b;font-size:13px">Thank you for shopping with %s.</p>
                </div>
                """.formatted(amount, orderNumber, storeName);
        send(buyerEmail, "Payment received - " + orderNumber, buyerHtml);

        if (sellerEmail != null && !sellerEmail.isBlank()) {
            String sellerHtml = """
                    <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;padding:24px;border:1px solid #e2e8f0;border-radius:12px">
                      <h2 style="color:#16a34a;margin-top:0">Payment received</h2>
                      <p>Order <strong>%s</strong> has been paid: <strong>N&#8358;%s</strong>.</p>
                      <p style="margin-bottom:0"><a href="https://carticom.cv/dashboard/orders" style="background:#2563eb;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;display:inline-block">Open dashboard</a></p>
                    </div>
                    """.formatted(orderNumber, amount);
            send(sellerEmail, "Payment received - " + orderNumber, sellerHtml);
        }
    }

    public void send(String to, String subject, String html) {
        if (!isConfigured()) {
            log.info("SendByte not configured (SENDBYTE_API_KEY empty) - skipping email to {}", to);
            return;
        }
        try {
            webClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "from", fromEmail,
                            "to", to,
                            "subject", subject,
                            "html", html
                    ))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("SendByte email sent to {} ({})", to, subject);
        } catch (WebClientResponseException e) {
            log.error("SendByte email to {} failed: {} {}", to, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("SendByte email to {} failed: {}", to, e.getMessage());
        }
    }
}
