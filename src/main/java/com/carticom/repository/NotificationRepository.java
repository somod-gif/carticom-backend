package com.carticom.repository;

import com.carticom.model.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(String userId);
    List<NotificationEntity> findByUserIdAndIsReadFalse(String userId);
    long countByUserIdAndIsReadFalse(String userId);
}
