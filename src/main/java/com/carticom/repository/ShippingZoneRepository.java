package com.carticom.repository;

import com.carticom.model.ShippingZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShippingZoneRepository extends JpaRepository<ShippingZone, Long> {
    List<ShippingZone> findByStoreIdOrderByIdAsc(Long storeId);
}
