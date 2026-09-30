package com.carticom.dto.store;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStoreSettingsRequest {

    private String name;

    private String category;

    @Pattern(regexp = "CLASSIC|MIDNIGHT|SUNBURST|BOTANICAL|MONO",
            message = "Unknown storefront theme")
    private String theme;

    @Pattern(regexp = "GRID|HERO_GRID|LIST",
            message = "Unknown storefront layout")
    private String layout;

    private Map<String, Object> business;

    private Map<String, Object> notifications;
}
