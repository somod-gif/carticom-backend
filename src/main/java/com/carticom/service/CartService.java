package com.carticom.service;

import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.*;
import com.carticom.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public Cart getOrCreateCart(Long storeId, String sessionId) {
        Optional<Cart> existing = cartRepository.findByStoreIdAndSessionIdAndStatus(
                storeId, sessionId, CartStatus.ACTIVE);
        return existing.orElseGet(() -> {
            Store store = new Store();
            store.setId(storeId);
            Cart cart = Cart.builder()
                    .store(store)
                    .sessionId(sessionId)
                    .status(CartStatus.ACTIVE)
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .build();
            return cartRepository.save(cart);
        });
    }

    @Transactional
    public Cart addItem(Long storeId, String sessionId, Long productId, int quantity) {
        Cart cart = getOrCreateCart(storeId, sessionId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst();

        if (existingItem.isPresent()) {
            existingItem.get().setQuantity(existingItem.get().getQuantity() + quantity);
        } else {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(quantity)
                    .unitPrice(product.getPrice())
                    .build();
            cart.getItems().add(item);
            cartItemRepository.save(item);
        }

        cart.setExpiresAt(LocalDateTime.now().plusHours(24));
        return cartRepository.save(cart);
    }

    @Transactional
    public Cart updateItemQuantity(Long storeId, String sessionId, Long productId, int quantity) {
        Cart cart = getOrCreateCart(storeId, sessionId);
        cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .ifPresent(item -> {
                    if (quantity <= 0) {
                        cart.getItems().remove(item);
                        cartItemRepository.delete(item);
                    } else {
                        item.setQuantity(quantity);
                    }
                });
        return cartRepository.save(cart);
    }

    @Transactional
    public void removeItem(Long storeId, String sessionId, Long productId) {
        Cart cart = getOrCreateCart(storeId, sessionId);
        cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .ifPresent(item -> {
                    cart.getItems().remove(item);
                    cartItemRepository.delete(item);
                });
        cartRepository.save(cart);
    }

    @Transactional
    public void convertCart(Long cartId) {
        cartRepository.findById(cartId).ifPresent(cart -> {
            cart.setStatus(CartStatus.CONVERTED);
            cartRepository.save(cart);
        });
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void markAbandonedCarts() {
        List<Cart> activeCarts = cartRepository.findByStatusAndExpiresAtBefore(
                CartStatus.ACTIVE, LocalDateTime.now());
        for (Cart cart : activeCarts) {
            cart.setStatus(CartStatus.ABANDONED);
            cartRepository.save(cart);
            log.info("Cart {} abandoned (store {}, session {})", cart.getId(),
                    cart.getStore().getId(), cart.getSessionId());
        }
    }

    public long getAbandonedCartCount(Long storeId) {
        return cartRepository.findByStoreIdAndStatus(storeId, CartStatus.ABANDONED).size();
    }

    public BigDecimal getAbandonedCartRevenue(Long storeId) {
        return cartRepository.findByStoreIdAndStatus(storeId, CartStatus.ABANDONED).stream()
                .flatMap(cart -> cart.getItems().stream())
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
