package com.carticom.repository;

import com.carticom.model.StaffInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StaffInviteRepository extends JpaRepository<StaffInvite, Long> {

    Optional<StaffInvite> findByToken(String token);

    List<StaffInvite> findByStoreIdOrderByCreatedAtDesc(Long storeId);

    Optional<StaffInvite> findByStoreIdAndEmailAndStatus(Long storeId, String email, StaffInvite.Status status);

    void deleteByStoreIdAndEmail(Long storeId, String email);

    long countByStatusAndExpiresAtBefore(StaffInvite.Status status, LocalDateTime before);
}
