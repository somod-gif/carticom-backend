package com.carticom.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "return_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequest {

    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stand-alone order reference — returns are customer-facing, no relationship to Order. */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** Authenticated customer's email — same identity orders are looked up by. */
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    /** What the customer says went wrong, in their own words. */
    @Column(nullable = false, length = 1000)
    private String reason;

    /** REQUESTED until a seller reviews it; defaults to REQUESTED. */
    @Column(nullable = false)
    @Builder.Default
    private String status = STATUS_REQUESTED;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
