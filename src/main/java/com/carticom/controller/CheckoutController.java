package com.carticom.controller;

import com.carticom.exception.BadRequestException;
import com.carticom.model.*;
import com.carticom.repository.CustomerRepository;
import com.carticom.repository.OrderRepository;
import com.carticom.repository.ProductRepository;
import com.carticom.service.CartService;
import com.carticom.service.NotificationService;
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
    private final NotificationService notificationService;

    public record ShippingAddress(String fullName, String phone, String address,
                                  String city, String state, String country) {}

    public record CheckoutRequest(String deliveryMethod, String notes, String couponCode,
                                  ShippingAddress shippingAddress,
                                  String customerName, String customerEmail, String customerPhone) {}

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

        boolean authenticated = authentication != null && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName());
        String authEmail = authenticated ? authentication.getName() : null;

        ShippingAddress ship = req != null ? req.shippingAddress() : null;

        // A3 — always capture customer contact (guest checkout included).
        String customerEmail = firstNonBlank(authEmail, req != null ? req.customerEmail() : null);
        String fullName = firstNonBlank(
                ship != null ? ship.fullName() : null,
                req != null ? req.customerName() : null,
                "Customer");
        String phone = firstNonBlank(
                ship != null ? ship.phone() : null,
                req != null ? req.customerPhone() : null);
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
            } else {
                if (customer.getName() == null || customer.getName().isBlank()) customer.setName(fullName);
                if (customer.getPhone() == null || customer.getPhone().isBlank()) customer.setPhone(phone);
                if (customer.getAddress() == null && addressLine != null) customer.setAddress(addressLine);
            }
            customer = customerRepository.save(customer);
        }

        // A4 — validate stock before creating the order (fail fast, no oversell).
        for (CartItem cartItem : cart.getItems()) {
            Product p = cartItem.getProduct();
            if (p == null || p.getStockQuantity() == null) continue;
            int available = p.getStockQuantity();
            if (available <= 0) {
                throw new BadRequestException("\"" + p.getName() + "\" is out of stock");
            }
            if (available < cartItem.getQuantity()) {
                throw new BadRequestException("Only " + available + " left of \"" + p.getName() + "\" in stock");
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
        order.setGuestSession(sessionId);

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
            if (product != null) {
                if (product.getStockQuantity() != null) {
                    product.setStockQuantity(Math.max(0, product.getStockQuantity() - cartItem.getQuantity()));
                }
                product.setSoldCount((product.getSoldCount() == null ? 0 : product.getSoldCount()) + cartItem.getQuantity());
                productRepository.save(product);
                notificationService.checkLowStock(storeId, product);
            }
        }

        order = orderRepository.save(order);
        cartService.convertCart(cart.getId());

        notificationService.notifyStoreTeam(storeId, "order",
                "New order " + order.getOrderNumber(),
                (fullName != null ? fullName : "A customer") + " placed an order of "
                        + order.getTotal().toPlainString() + " NGN");

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
    public Map<String, Object> getOrder(
            Authentication authentication,
            @RequestHeader(value = "X-Cart-Session", required = false) String sessionHeader,
            HttpServletRequest request,
            @PathVariable Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new com.carticom.exception.ResourceNotFoundException("Order not found"));
        // A9 — order lookup must be authorized: owner (customer/staff/seller) or
        // the guest cart session that placed it. Everyone else gets a 404.
        if (!canAccessOrder(order, authentication, sessionHeader, request)) {
            throw new com.carticom.exception.ResourceNotFoundException("Order not found");
        }
        return mapOrder(order, null);
    }

    @PostMapping("/orders/{id}/cancel")
    public Map<String, Object> cancelOrder(
            Authentication authentication,
            @RequestHeader(value = "X-Cart-Session", required = false) String sessionHeader,
            HttpServletRequest request,
            @PathVariable Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new com.carticom.exception.ResourceNotFoundException("Order not found"));
        if (!canAccessOrder(order, authentication, sessionHeader, request)) {
            throw new com.carticom.exception.ResourceNotFoundException("Order not found");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Order can no longer be cancelled");
        }
        // Restore stock the checkout deducted.
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product != null) {
                if (product.getStockQuantity() != null) {
                    product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                }
                if (product.getSoldCount() != null) {
                    product.setSoldCount(Math.max(0, product.getSoldCount() - item.getQuantity()));
                }
                productRepository.save(product);
            }
        }
        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);
        if (order.getStore() != null && order.getStore().getId() != null) {
            notificationService.notifyStoreTeam(order.getStore().getId(), "order",
                    "Order " + order.getOrderNumber() + " cancelled",
                    "The customer cancelled this pending order. Stock has been restored.");
        }
        return mapOrder(order, null);
    }

    /** True when the caller owns this order (logged-in customer, store staff/seller,
     *  or the guest session that placed it). */
    private boolean canAccessOrder(Order order, Authentication authentication,
                                   String sessionHeader, HttpServletRequest request) {
        String guestSession = resolveSessionOrNull(authentication, request);
        if (order.getGuestSession() != null && guestSession != null
                && order.getGuestSession().equals(guestSession)) {
            return true;
        }
        String headerSession = sessionHeader != null && !sessionHeader.isBlank() ? sessionHeader.trim() : null;
        if (order.getGuestSession() != null && headerSession != null
                && order.getGuestSession().equals(headerSession)) {
            return true;
        }
        boolean authenticated = authentication != null && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName());
        if (!authenticated) return false;
        String email = authentication.getName();
        if (order.getCustomer() != null && email.equals(order.getCustomer().getEmail())) {
            return true;
        }
        Store store = order.getStore();
        if (store != null && store.getSeller() != null && email.equals(store.getSeller().getEmail())) {
            return true;
        }
        return false;
    }

    private String resolveSession(String header, Authentication authentication, HttpServletRequest request) {
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String session = resolveSessionOrNull(authentication, request);
        if (session != null) {
            return session;
        }
        throw new BadRequestException("Cart session not found. Please refresh and try again.");
    }

    private String resolveSessionOrNull(Authentication authentication, HttpServletRequest request) {
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
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
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
