package com.carticom.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    // ─── Storefront branding / customisation (edited from the dashboard) ───

    /** Storefront template id, e.g. {@code fashion-luxury}. */
    private String template;

    private String primaryColor;

    private String secondaryColor;

    private String fontFamily;

    // ─── Social / contact links ───

    private String facebookUrl;

    private String instagramUrl;

    private String twitterUrl;

    private String whatsappNumber;

    // ─── SEO ───

    private String seoTitle;

    @Column(columnDefinition = "text")
    private String seoDescription;

    @Column(columnDefinition = "text")
    private String customCss;

    /** Ordered storefront sections as a JSON array, e.g. ["hero","showcase"]. */
    @Column(columnDefinition = "text")
    private String sectionConfig;

    /** Marquee shown above the storefront header; null when there is none. */
    private String announcementBar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /** Last time branding/settings were written; null until the first update. */
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
