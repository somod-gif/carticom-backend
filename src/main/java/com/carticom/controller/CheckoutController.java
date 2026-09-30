package com.carticom.controller;

import com.carticom.exception.BadRequestException;
import com.carticom.model.*;
import com.carticom.repository.CustomerRepository;
import com.carticom.repository.OrderRepository;
import com.carticom.repository.ProductRepository;
import com.carticom.service.CartService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
@Transactional
public class CheckoutController {

    private final CartService cartService;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public record ShippingAddress(String fullName, String phone, String address,
                                  String city, String state, String country) {}

    public record CheckoutRequest(String deliveryMethod, String notes, String couponCode,
                                  ShippingAddress shippingAddress) {}

    @PostMapping
    public Map<String, Object> checkout(
            Authentication authentication,
            @RequestParam Long storeId,
            @RequestHeader(value = "X-Cart-Session", required = false) String sessionHeader,
            HttpServletRequest request,
            @RequestBody(required = false) CheckoutRequest req) {
        String sessionId = resolveSession(sessionHeader, authentication, request);
        Cart cart = cartService.getOrCreateCart(storeId, sessionId);
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Your cart is empty");
        }

        String customerEmail = authentication != null && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName()) ? authentication.getName() : null;

        ShippingAddress ship = req != null ? req.shippingAddress() : null;
        String fullName = ship != null && ship.fullName() != null ? ship.fullName() : "Customer";
        String phone = ship != null && ship.phone() != null ? ship.phone() : null;
        String addressLine = ship != null ? buildAddress(ship) : null;

        Customer customer = null;
        if (customerEmail != null && !customerEmail.isBlank()) {
            customer = customerRepository.findByStoreIdAndEmail(storeId, customerEmail).orElse(null);
            if (customer == null) {
                customer = new Customer();
                customer.setStore(refStore(storeId));
                customer.setEmail(customerEmail);
                customer.setName(fullName);
                customer.setPhone(phone);
                customer.setAddress(addressLine);
                customer.setTotalOrders(0);
                customer = customerRepository.save(customer);
            }
        }

        BigDecimal subtotal = cart.getItems().stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order();
        order.setOrderNumber("CTM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        order.setStore(refStore(storeId));
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setSubtotal(subtotal);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setTotal(subtotal);
        order.setDeliveryAddress(addressLine);
        order.setDeliveryPhone(phone);
        order.setDeliveryNotes(req != null ? req.notes() : null);
        order.setChannel(OrderChannel.STOREFRONT);

        for (CartItem cartItem : cart.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setProductName(cartItem.getProduct().getName());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setUnitPrice(cartItem.getUnitPrice());
            orderItem.setTotalPrice(cartItem.getUnitPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            order.getItems().add(orderItem);

            Product product = cartItem.getProduct();
            if (product != null && product.getStockQuantity() != null) {
                int newStock = Math.max(0, product.getStockQuantity() - cartItem.getQuantity());
                product.setStockQuantity(newStock);
                productRepository.save(product);
            }
        }

        order = orderRepository.save(order);
        cartService.convertCart(cart.getId());

        return mapOrder(order, order.getItems());
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> myOrders(Authentication authentication) {
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new BadRequestException("Please sign in to view your orders");
        }
        return orderRepository.findByCustomerEmailOrderByCreatedAtDesc(authentication.getName()).stream()
                .map(o -> mapOrder(o, null))
                .toList();
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> getOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new com.carticom.exception.ResourceNotFoundException("Order not found"));
        return mapOrder(order, null);
    }

    @PostMapping("/orders/{id}/cancel")
    public Map<String, Object> cancelOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new com.carticom.exception.ResourceNotFoundException("Order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Order can no longer be cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);
        return mapOrder(order, null);
    }

    private String resolveSession(String header, Authentication authentication, HttpServletRequest request) {
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        if (authentication != null && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            return "cust:" + authentication.getName();
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("cart_sid".equals(cookie.getName()) && cookie.getValue() != null) {
                    return cookie.getValue();
                }
            }
        }
        throw new BadRequestException("Cart session not found. Please refresh and try again.");
    }

    private String buildAddress(ShippingAddress ship) {
        StringBuilder sb = new StringBuilder();
        if (ship.address() != null) sb.append(ship.address());
        if (ship.city() != null && !ship.city().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(ship.city());
        }
        if (ship.state() != null && !ship.state().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(ship.state());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private Store refStore(Long storeId) {
        Store store = new Store();
        store.setId(storeId);
        return store;
    }

    private Map<String, Object> mapOrder(Order order, List<OrderItem> lazyItems) {
        List<OrderItem> items = lazyItems != null ? lazyItems : order.getItems();
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", order.getId());
        dto.put("storeId", order.getStore() != null ? order.getStore().getId() : null);
        dto.put("customerId", order.getCustomer() != null
                ? String.valueOf(order.getCustomer().getId()) : null);
        dto.put("orderNumber", order.getOrderNumber());
        dto.put("status", mapStatus(order.getStatus()));
        dto.put("paymentStatus", mapPaymentStatus(order.getPaymentStatus()));
        dto.put("subtotal", order.getSubtotal());
        dto.put("shipping", order.getDeliveryFee());
        dto.put("tax", order.getTaxAmount());
        dto.put("discount", BigDecimal.ZERO);
        dto.put("total", order.getTotal());
        dto.put("currency", "NGN");
        dto.put("deliveryAddress", order.getDeliveryAddress());
        dto.put("customerEmail", order.getCustomer() != null ? order.getCustomer().getEmail() : null);
        dto.put("customerPhoneNumber", order.getDeliveryPhone());
        dto.put("customerName", order.getCustomer() != null ? order.getCustomer().getName() : null);
        dto.put("notes", order.getDeliveryNotes());
        dto.put("channel", order.getChannel() != null ? order.getChannel().name() : null);
        dto.put("items", items.stream().map(this::mapItem).toList());
        dto.put("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
        dto.put("updatedAt", order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null);
        return dto;
    }

    private Map<String, Object> mapItem(OrderItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("productId", item.getProduct() != null ? item.getProduct().getId() : null);
        m.put("productName", item.getProductName());
        m.put("productImage", item.getProduct() != null ? item.getProduct().getImageUrl() : null);
        m.put("unitPrice", item.getUnitPrice());
        m.put("quantity", item.getQuantity());
        m.put("lineTotal", item.getTotalPrice());
        return m;
    }

    private String mapStatus(OrderStatus status) {
        if (status == null) return "PENDING";
        return switch (status) {
            case CONFIRMED -> "PROCESSING";
            case REFUNDED -> "CANCELLED";
            default -> status.name();
        };
    }

    private String mapPaymentStatus(PaymentStatus status) {
        if (status == null) return "PENDING";
        return switch (status) {
            case PAID -> "COMPLETED";
            case PARTIALLY_REFUNDED -> "REFUNDED";
            default -> status.name();
        };
    }
}
