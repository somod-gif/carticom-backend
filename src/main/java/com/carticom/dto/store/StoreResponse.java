package com.carticom.dto.store;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreResponse {
    private Long id;
    private String name;
    private String slug;
    private String category;
    private String theme;
    private String layout;
    private String description;
    private String email;
    private String phone;
    private String address;
    private String country;
    private String currency;
    private Map<String, Object> business;
    private Map<String, Object> notifications;
    private String logoUrl;
    private String bannerUrl;
    private String status;
    private String template;
    private String primaryColor;
    private String secondaryColor;
    private String fontFamily;
    private String facebookUrl;
    private String instagramUrl;
    private String twitterUrl;
    private String whatsappNumber;
    private String seoTitle;
    private String seoDescription;
    private String customCss;
    /** Marquee shown above the storefront header (null when there is none). */
    private String announcementBar;
    /** Ordered storefront sections as a JSON array, e.g. ["hero","showcase"]. */
    private String sectionConfig;
    private String sellerEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
