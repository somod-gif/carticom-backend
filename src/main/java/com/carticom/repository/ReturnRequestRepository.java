package com.carticom.repository;

import com.carticom.model.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    List<ReturnRequest> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    boolean existsByOrderIdAndStatus(Long orderId, String status);

    List<ReturnRequest> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
