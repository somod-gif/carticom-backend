package com.carticom.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer maxProducts;

    @Column(nullable = false)
    private Integer maxOrdersPerMonth;

    @Column(nullable = false)
    private Integer maxCustomers;

    private Boolean hasAnalytics;
    private Boolean hasAiFeatures;
    private Boolean hasPrioritySupport;
    private Boolean hasCustomDomain;
    private Boolean hasRemoveBranding;

    @Column(length = 1000)
    private String featuresJson;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
