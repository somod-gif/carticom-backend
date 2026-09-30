package com.carticom.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String category;

    @Builder.Default
    @Column(nullable = false)
    private String theme = "CLASSIC";

    @Builder.Default
    @Column(nullable = false)
    private String layout = "GRID";

    private String description;

    private String email;

    private String phone;

    private String address;

    private String country;

    private String currency;

    @Column(columnDefinition = "text")
    private String notifications;

    private String logoUrl;

    private String bannerUrl;

    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
