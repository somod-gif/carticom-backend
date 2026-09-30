package com.carticom.repository;

import com.carticom.model.ShippingMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, Long> {
    List<ShippingMethod> findByStoreIdOrderByIdAsc(Long storeId);
}
