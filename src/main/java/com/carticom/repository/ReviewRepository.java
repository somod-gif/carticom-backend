package com.carticom.repository;

import com.carticom.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdAndStatusOrderByCreatedAtDesc(Long productId, String status);

    boolean existsByProductIdAndUserId(Long productId, String userId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.productId = ?1 AND r.status = ?2")
    Double getAverageRating(Long productId, String status);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.productId = ?1 AND r.status = ?2")
    Long getCount(Long productId, String status);
}
