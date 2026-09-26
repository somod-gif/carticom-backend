package com.carticom.repository;

import com.carticom.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByStoreId(Long storeId);

    Optional<Customer> findByStoreIdAndEmail(Long storeId, String email);

    boolean existsByStoreIdAndEmail(Long storeId, String email);

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.store.id = ?1")
    Long countCustomers(Long storeId);
}
