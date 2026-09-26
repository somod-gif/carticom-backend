package com.carticom.repository;

import com.carticom.model.InventoryStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, Long> {

    List<InventoryStock> findByProductId(Long productId);

    Optional<InventoryStock> findByProductIdAndLocationId(Long productId, Long locationId);

    List<InventoryStock> findByLocationId(Long locationId);
}
