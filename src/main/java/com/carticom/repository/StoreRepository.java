package com.carticom.repository;

import com.carticom.model.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findBySellerId(Long sellerId);

    Optional<Store> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySellerIdAndName(Long sellerId, String name);
}
