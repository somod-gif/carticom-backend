package com.carticom.controller;

import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Order;
import com.carticom.model.Payment;
import com.carticom.repository.OrderRepository;
import com.carticom.repository.PaymentRepository;
import com.carticom.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GuestTrackController {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @GetMapping({"/orders/track/{reference}", "/guest-checkout/track/{reference}"})
    public Map<String, Object> track(@PathVariable String reference) {
        Order order = orderRepository.findByOrderNumber(reference).orElse(null);
        if (order == null) {
            Payment payment = paymentRepository.findByReference(reference).orElse(null);
            if (payment != null) {
                order = payment.getOrder();
            }
        }
        if (order == null) {
            throw new ResourceNotFoundException("Order not found");
        }

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", order.getId());
        dto.put("orderNumber", order.getOrderNumber());
        dto.put("status", order.getStatus() != null ? order.getStatus().name() : "PENDING");
        dto.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "PENDING");
        dto.put("total", order.getTotal());
        dto.put("subtotal", order.getSubtotal());
        dto.put("currency", "NGN");
        dto.put("deliveryAddress", order.getDeliveryAddress());
        dto.put("storeName", order.getStore() != null ? order.getStore().getName() : null);
        dto.put("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
        List<Map<String, Object>> items = order.getItems().stream().map(item -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productName", item.getProductName());
            m.put("quantity", item.getQuantity());
            m.put("unitPrice", item.getUnitPrice());
            m.put("lineTotal", item.getTotalPrice());
            return m;
        }).toList();
        dto.put("items", items);
        return dto;
    }
}
