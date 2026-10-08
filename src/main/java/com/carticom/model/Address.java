package com.carticom.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Authenticated customer's email — the same identity orders and returns are looked up by. */
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    /** What the customer calls this place — e.g. "Home" or "Work". */
    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, length = 255)
    private String street;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(length = 30)
    private String phone;

    /** The one address delivery falls back to — every customer keeps at least one. */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
