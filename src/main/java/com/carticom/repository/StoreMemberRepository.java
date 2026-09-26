package com.carticom.repository;

import com.carticom.model.StoreMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface StoreMemberRepository extends JpaRepository<StoreMember, Long> {

    Optional<StoreMember> findFirstByUserId(Long userId);

    List<StoreMember> findByStoreId(Long storeId);

    boolean existsByStoreIdAndUserId(Long storeId, Long userId);

    Optional<StoreMember> findByStoreIdAndUserId(Long storeId, Long userId);

    @Modifying
    @Transactional
    @Query("delete from StoreMember m where m.store.id = :storeId and m.user.id = :userId")
    void deleteByStoreIdAndUserId(Long storeId, Long userId);
}
