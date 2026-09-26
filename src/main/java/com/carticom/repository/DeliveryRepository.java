package com.carticom.repository;

import com.carticom.model.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    List<Delivery> findByOrderId(Long orderId);

    List<Delivery> findByOrderStoreId(Long storeId);

    List<Delivery> findByOrderStoreIdAndStatus(Long storeId, com.carticom.model.DeliveryStatus status);
}
