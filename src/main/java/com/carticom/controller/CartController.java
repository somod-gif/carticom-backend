package com.carticom.controller;

import com.carticom.exception.BadRequestException;
import com.carticom.model.Cart;
import com.carticom.model.CartItem;
import com.carticom.repository.CartItemRepository;
import com.carticom.service.CartService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Transactional
public class CartController {

    private final CartService cartService;
    private final CartItemRepository cartItemRepository;

    @GetMapping
    public Map<String, Object> getCart(
            @RequestParam Long storeId,
            @RequestHeader(value = "X-Cart-Session", required = false) String sessionHeader,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        String sessionId = resolveSession(sessionHeader, authentication, request, response);
        return mapCart(cartService.getOrCreateCart(storeId, sessionId));
    }

    @PostMapping("/items")
    public Map<String, Object> addItem(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Cart-Session", required = false) String sessionHeader,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        Long storeId = asLong(body.get("storeId"));
        Long productId = asLong(body.get("productId"));
        int quantity = body.get("quantity") != null ? Integer.parseInt(String.valueOf(body.get("quantity"))) : 1;
        if (storeId == null || productId == null) {
            throw new BadRequestException("storeId and productId are required");
        }
        if (quantity < 1) {
            quantity = 1;
        }
        String sessionId = resolveSession(sessionHeader, authentication, request, response);
        return mapCart(cartService.addItem(storeId, sessionId, productId, quantity));
    }

    @PutMapping("/items")
    public Map<String, Object> updateItem(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Cart-Session", required = false) String sessionHeader,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        Long storeId = asLong(body.get("storeId"));
        Long productId = asLong(body.get("productId"));
        int quantity = body.get("quantity") != null ? Integer.parseInt(String.valueOf(body.get("quantity"))) : 1;
        if (storeId == null || productId == null) {
            throw new BadRequestException("storeId and productId are required");
        }
        String sessionId = resolveSession(sessionHeader, authentication, request, response);
        return mapCart(cartService.updateItemQuantity(storeId, sessionId, productId, quantity));
    }

    @DeleteMapping("/items")
    public Map<String, Object> removeItem(
            @RequestParam Long storeId,
            @RequestParam Long productId,
            @RequestHeader(value = "X-Cart-Session", required = false) String sessionHeader,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        String sessionId = resolveSession(sessionHeader, authentication, request, response);
        cartService.removeItem(storeId, sessionId, productId);
        return mapCart(cartService.getOrCreateCart(storeId, sessionId));
    }

    @DeleteMapping("/clear")
    public Map<String, Object> clearCart(
            @RequestParam Long storeId,
            @RequestHeader(value = "X-Cart-Session", required = false) String sessionHeader,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        String sessionId = resolveSession(sessionHeader, authentication, request, response);
        Cart cart = cartService.getOrCreateCart(storeId, sessionId);
        cartItemRepository.deleteByCartId(cart.getId());
        cart.getItems().clear();
        return mapCart(cart);
    }

    private String resolveSession(String header, Authentication authentication,
                                  HttpServletRequest request, HttpServletResponse response) {
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
        String sid = UUID.randomUUID().toString().replace("-", "");
        Cookie cookie = new Cookie("cart_sid", sid);
        cookie.setPath("/");
        cookie.setMaxAge(86400);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        response.addCookie(cookie);
        return sid;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> mapCart(Cart cart) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", cart.getId());
        dto.put("storeId", cart.getStore() != null ? cart.getStore().getId() : null);
        dto.put("customerId", cart.getCustomerEmail());
        dto.put("status", cart.getStatus() != null ? cart.getStatus().name() : "ACTIVE");

        List<Map<String, Object>> items = cart.getItems().stream().map(this::mapItem).toList();
        BigDecimal subtotal = items.stream()
                .map(i -> (BigDecimal) i.get("lineTotal"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        dto.put("subtotal", subtotal);
        dto.put("shipping", BigDecimal.ZERO);
        dto.put("discount", BigDecimal.ZERO);
        dto.put("total", subtotal);
        dto.put("currency", "NGN");
        dto.put("items", items);
        dto.put("createdAt", cart.getCreatedAt() != null ? cart.getCreatedAt().toString() : null);
        dto.put("updatedAt", cart.getUpdatedAt() != null ? cart.getUpdatedAt().toString() : null);
        return dto;
    }

    private Map<String, Object> mapItem(CartItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("cartId", item.getCart() != null ? item.getCart().getId() : null);
        m.put("productId", item.getProduct() != null ? item.getProduct().getId() : null);
        m.put("productName", item.getProduct() != null ? item.getProduct().getName() : null);
        m.put("productImage", item.getProduct() != null ? item.getProduct().getImageUrl() : null);
        m.put("quantity", item.getQuantity());
        m.put("unitPrice", item.getUnitPrice());
        m.put("lineTotal", item.getUnitPrice() != null
                ? item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                : BigDecimal.ZERO);
        String addedAt = item.getAddedAt() != null ? item.getAddedAt().toString() : null;
        m.put("createdAt", addedAt);
        m.put("updatedAt", addedAt);
        return m;
    }
}
