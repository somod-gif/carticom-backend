package com.carticom.controller;

import com.carticom.dto.payment.PaymentInitResponse;
import com.carticom.dto.payment.PaymentVerifyResponse;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.*;
import com.carticom.repository.CustomerRepository;
import com.carticom.repository.OrderRepository;
import com.carticom.repository.PaymentRepository;
import com.carticom.repository.ProductRepository;
import com.carticom.service.NotificationService;
import com.carticom.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/guest-checkout")
@RequiredArgsConstructor
public class GuestCheckoutController {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    public record GuestItem(String productId, Integer quantity) {}

    public record GuestAddress(String fullName, String street, String city,
                               String state, String country, String zipCode) {}

    public record GuestCheckoutRequest(Long storeId, List<GuestItem> items, String email, String phone,
                                       GuestAddress shippingAddress, String couponCode) {}

    public record GuestPayRequest(String referenceCode, String paymentProvider, String callbackUrl) {}

    public record GuestPayConfirmRequest(String transactionId, String providerReference) {}

    @PostMapping
    public Map<String, Object> create(@RequestBody GuestCheckoutRequest req) {
        if (req.storeId() == null || req.items() == null || req.items().isEmpty()) {
            throw new BadRequestException("storeId and items are required");
        }
        if (req.email() == null || !req.email().contains("@")) {
            throw new BadRequestException("A valid email is required");
        }

        Store store = new Store();
        store.setId(req.storeId());

        Order order = new Order();
        order.setOrderNumber("CTM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        order.setStore(store);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setChannel(OrderChannel.STOREFRONT);

        Customer customer = customerRepository.findByStoreIdAndEmail(req.storeId(), req.email()).orElse(null);
        if (customer == null) {
            customer = new Customer();
            customer.setStore(store);
            customer.setEmail(req.email());
            customer.setName(req.shippingAddress() != null && req.shippingAddress().fullName() != null
                    ? req.shippingAddress().fullName() : "Guest");
            customer.setPhone(req.phone());
            customer.setTotalOrders(0);
            customer = customerRepository.save(customer);
        }
        order.setCustomer(customer);

        String address = req.shippingAddress() != null
                ? String.join(", ", stripNulls(req.shippingAddress().street(), req.shippingAddress().city(),
                        req.shippingAddress().state()))
                : null;
        order.setDeliveryAddress(address);
        order.setDeliveryPhone(req.phone());

        BigDecimal subtotal = BigDecimal.ZERO;
        for (GuestItem item : req.items()) {
            Product product = productRepository.findById(Long.parseLong(item.productId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            int qty = item.quantity() != null ? item.quantity() : 1;
            BigDecimal line = product.getPrice().multiply(BigDecimal.valueOf(qty));
            subtotal = subtotal.add(line);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setQuantity(qty);
            orderItem.setUnitPrice(product.getPrice());
            orderItem.setTotalPrice(line);
            order.getItems().add(orderItem);

            if (product.getStockQuantity() != null) {
                product.setStockQuantity(Math.max(0, product.getStockQuantity() - qty));
                productRepository.save(product);
                notificationService.checkLowStock(req.storeId(), product);
            }
        }
        order.setSubtotal(subtotal);
        order.setTotal(subtotal);
        order = orderRepository.save(order);

        notificationService.notifyStoreTeam(req.storeId(), "order",
                "New order " + order.getOrderNumber(),
                (order.getCustomer() != null && order.getCustomer().getName() != null
                        ? order.getCustomer().getName() : "A customer")
                        + " placed an order of " + order.getTotal().toPlainString() + " NGN");

        String callback = appBaseUrl + "/payment/callback?orderId=" + order.getId()
                + "&guest=" + order.getOrderNumber();
        String reference = order.getOrderNumber();
        String paymentUrl = null;
        try {
            PaymentInitResponse init = paymentService.initializeForOrder(
                    order.getId(), "paystack", req.email(), callback);
            reference = init.getReference() != null ? init.getReference() : reference;
            paymentUrl = init.getAuthorizationUrl();
        } catch (Exception e) {
            // payment provider unavailable; order still saved
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("orderId", String.valueOf(order.getId()));
        res.put("orderNumber", order.getOrderNumber());
        res.put("reference", reference);
        res.put("total", order.getTotal());
        res.put("paymentUrl", paymentUrl);
        res.put("status", order.getPaymentStatus().name());
        return res;
    }

    @PostMapping("/pay")
    public Map<String, Object> pay(@RequestBody GuestPayRequest req) {
        if (req.referenceCode() == null || req.referenceCode().isBlank()) {
            throw new BadRequestException("referenceCode is required");
        }
        Order order = orderRepository.findByOrderNumber(req.referenceCode()).orElse(null);
        if (order == null) {
            Payment existing = paymentRepository.findByReference(req.referenceCode()).orElse(null);
            if (existing != null) {
                order = existing.getOrder();
            }
        }
        if (order == null) {
            throw new ResourceNotFoundException("Order not found");
        }
        String provider = req.paymentProvider() != null && !req.paymentProvider().isBlank()
                ? req.paymentProvider() : "paystack";
        String callback = req.callbackUrl() != null && !req.callbackUrl().isBlank()
                ? req.callbackUrl()
                : appBaseUrl + "/payment/callback?orderId=" + order.getId() + "&guest=" + order.getOrderNumber();

        PaymentInitResponse init = paymentService.initializeForOrder(order.getId(), provider,
                order.getCustomer() != null ? order.getCustomer().getEmail() : null, callback);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("transactionId", init.getReference());
        res.put("status", "PENDING");
        res.put("paymentProvider", provider);
        res.put("paymentMethod", "CARD");
        res.put("authorizationUrl", init.getAuthorizationUrl());
        res.put("providerReference", init.getReference());
        res.put("message", "Redirecting to payment");
        return res;
    }

    @PostMapping("/pay/confirm")
    public Map<String, Object> payConfirm(@RequestBody GuestPayConfirmRequest req) {
        String reference = req.providerReference() != null && !req.providerReference().isBlank()
                ? req.providerReference() : req.transactionId();
        if (reference == null || reference.isBlank()) {
            throw new BadRequestException("providerReference or transactionId is required");
        }
        PaymentVerifyResponse result = paymentService.verifyAndSettle(reference);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("transactionId", reference);
        res.put("status", Boolean.TRUE.equals(result.getVerified()) ? "SUCCESS"
                : (result.getStatus() != null ? result.getStatus() : "FAILED"));
        res.put("paymentProvider", result.getProvider());
        res.put("paymentMethod", "CARD");
        res.put("providerReference", reference);
        res.put("message", result.getGatewayResponse());
        return res;
    }

    private List<String> stripNulls(String... values) {
        List<String> out = new java.util.ArrayList<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(v);
            }
        }
        return out;
    }
}
