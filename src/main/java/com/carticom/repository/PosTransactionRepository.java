package com.carticom.repository;

import com.carticom.model.PosTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PosTransactionRepository extends JpaRepository<PosTransaction, Long> {

    List<PosTransaction> findByStoreIdOrderByCreatedAtDesc(Long storeId);
}
