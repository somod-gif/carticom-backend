package com.carticom.repository;

import com.carticom.model.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {
    boolean existsByEmail(String email);
}
