package com.carticom.repository;

import com.carticom.model.InventoryLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryLocationRepository extends JpaRepository<InventoryLocation, Long> {

    List<InventoryLocation> findByStoreId(Long storeId);
}
