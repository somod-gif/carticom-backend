package com.carticom.repository;

import com.carticom.model.StorePaymentConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StorePaymentConfigRepository extends JpaRepository<StorePaymentConfig, Long> {
    Optional<StorePaymentConfig> findByStoreId(Long storeId);
}
