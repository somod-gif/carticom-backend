package com.carticom.dto.store;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial update for the storefront branding block of a store.
 *
 * <p>Semantics: {@code null} leaves a field untouched, an empty string clears it
 * (for the free-text fields that allow clearing), and anything present must pass
 * its validation rule. {@code sectionConfig} is additionally parsed and whitelisted
 * in {@code StoreService#updateBranding}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStoreBrandingRequest {

    /** Storefront template id, e.g. {@code fashion-luxury}. */
    @Size(max = 50, message = "template must be at most 50 characters")
    @Pattern(regexp = "^([a-z0-9-]+)?$",
            message = "template may only contain lowercase letters, digits and dashes")
    private String template;

    /** Primary brand colour as a hex triplet, e.g. {@code #4f46e5}. */
    @Pattern(regexp = "^(#[0-9A-Fa-f]{6})?$", message = "primaryColor must be a hex colour like #4f46e5")
    private String primaryColor;

    @Pattern(regexp = "^(#[0-9A-Fa-f]{6})?$", message = "secondaryColor must be a hex colour like #4f46e5")
    private String secondaryColor;

    @Size(max = 100, message = "fontFamily must be at most 100 characters")
    private String fontFamily;

    /** Absolute http(s) URL or an app-relative path such as {@code /image/logo.png}. */
    @Size(max = 500, message = "logoUrl must be at most 500 characters")
    @Pattern(regexp = "^((https?://|/).*)?$",
            message = "logoUrl must start with http://, https:// or /")
    private String logoUrl;

    @Size(max = 500, message = "bannerUrl must be at most 500 characters")
    @Pattern(regexp = "^((https?://|/).*)?$",
            message = "bannerUrl must start with http://, https:// or /")
    private String bannerUrl;

    @Size(max = 300, message = "facebookUrl must be at most 300 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "facebookUrl must start with http:// or https://")
    private String facebookUrl;

    @Size(max = 300, message = "instagramUrl must be at most 300 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "instagramUrl must start with http:// or https://")
    private String instagramUrl;

    @Size(max = 300, message = "twitterUrl must be at most 300 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "twitterUrl must start with http:// or https://")
    private String twitterUrl;

    @Size(max = 30, message = "whatsappNumber must be at most 30 characters")
    private String whatsappNumber;

    /** Search result title; keep it under ~70 characters so it is not truncated. */
    @Size(max = 70, message = "seoTitle must be at most 70 characters")
    private String seoTitle;

    @Size(max = 160, message = "seoDescription must be at most 160 characters")
    private String seoDescription;

    /** Storefront CSS injected into a style block; sanitised in the service. */
    @Size(max = 10000, message = "customCss must be at most 10000 characters")
    private String customCss;

    /** Marquee shown above the storefront; an empty string clears it. */
    @Size(max = 200, message = "announcementBar must be at most 200 characters")
    private String announcementBar;

    /**
     * Ordered storefront sections as a JSON array of known tokens, e.g.
     * {@code ["hero","showcase","testimonials"]}.
     */
    @Size(max = 2000, message = "sectionConfig must be at most 2000 characters")
    private String sectionConfig;
}
