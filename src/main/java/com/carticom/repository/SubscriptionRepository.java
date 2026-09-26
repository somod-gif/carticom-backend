package com.carticom.repository;

import com.carticom.model.Subscription;
import com.carticom.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByStoreIdAndStatus(Long storeId, SubscriptionStatus status);
    List<Subscription> findByStoreIdOrderByCreatedAtDesc(Long storeId);
    boolean existsByStoreIdAndStatus(Long storeId, SubscriptionStatus status);
}
