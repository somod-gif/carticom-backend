package com.carticom.repository;

import com.carticom.model.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {
    boolean existsByEmail(String email);
    List<WaitlistEntry> findByStatus(String status);
    Optional<WaitlistEntry> findByEmail(String email);
    long countByStatusAndCreatedAtBefore(String status, LocalDateTime createdAt);
}
