package com.carticom.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reviews_product_user", columnNames = {"product_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Authenticated user's email — reviews stand alone, no relationship to Product. */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /** Display name captured when the review was posted. */
    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 1000)
    private String comment;

    /** PENDING until a store owner approves it; only APPROVED reviews are shown. */
    @Column(nullable = false)
    @Builder.Default
    private String status = STATUS_PENDING;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
