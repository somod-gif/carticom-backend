package com.carticom.repository;

import com.carticom.model.Payment;
import com.carticom.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderId(Long orderId);

    List<Payment> findByOrderStoreIdAndStatus(Long storeId, PaymentStatus status);

    List<Payment> findByOrderStoreId(Long storeId);

    Optional<Payment> findByReference(String reference);

    Optional<Payment> findByGatewayReference(String gatewayReference);

    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);
}
