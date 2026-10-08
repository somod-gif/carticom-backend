package com.carticom.repository;

import com.carticom.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    long countByCustomerId(String customerId);
}
