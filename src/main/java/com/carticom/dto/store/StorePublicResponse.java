package com.carticom.dto.store;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorePublicResponse {
    private Long id;
    private String name;
    private String slug;
    private String category;
    private String theme;
    private String layout;
    private Integer productCount;
    private String description;
    private String email;
    private String phone;
    private String address;
    private String country;
    private String currency;
    private String logoUrl;
    private String bannerUrl;
    private String template;
    private String primaryColor;
    private String secondaryColor;
    private String fontFamily;
    private String facebookUrl;
    private String instagramUrl;
    private String twitterUrl;
    private String whatsappNumber;
    private String status;
    private LocalDateTime createdAt;
}
