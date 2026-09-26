package com.carticom.repository;

import com.carticom.model.Cart;
import com.carticom.model.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByStoreIdAndSessionIdAndStatus(Long storeId, String sessionId, CartStatus status);

    List<Cart> findByStoreIdAndStatus(Long storeId, CartStatus status);

    List<Cart> findByStatusAndExpiresAtBefore(CartStatus status, LocalDateTime expiresAt);
}
