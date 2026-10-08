package com.carticom.service;

import com.carticom.dto.store.CreateStoreRequest;
import com.carticom.dto.store.StoreResponse;
import com.carticom.dto.store.UpdateStoreBrandingRequest;
import com.carticom.dto.store.UpdateStoreSettingsRequest;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Store;
import com.carticom.model.User;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreService {

    private static final Set<String> THEMES = Set.of("CLASSIC", "MIDNIGHT", "SUNBURST", "BOTANICAL", "MONO");
    private static final Set<String> LAYOUTS = Set.of("GRID", "HERO_GRID", "LIST");

    /** Sections the storefront knows how to render; the only tokens sectionConfig accepts. */
    private static final Set<String> SECTION_TOKENS = Set.of(
            "hero", "showcase", "storytelling", "values", "membership", "testimonials",
            "features", "categories", "instagram", "faq", "newsletter", "announcement");

    private static final String SECTION_CONFIG_ERROR = "sectionConfig must be a JSON array of known "
            + "sections, for example [\"hero\",\"showcase\"]";

    // ─── Caps for the legacy onboarding payload handled by updateStore(Map) ───
    private static final int NAME_MAX = 500;
    private static final int SLUG_MAX = 200;
    private static final int DESCRIPTION_MAX = 2000;
    private static final int CATEGORY_MAX = 100;
    private static final int TEMPLATE_MAX = 50;
    private static final int COLOR_MAX = 7;
    private static final int FONT_MAX = 100;
    private static final int EMAIL_MAX = 200;
    private static final int PHONE_MAX = 50;
    private static final int ADDRESS_MAX = 500;
    private static final int COUNTRY_MAX = 100;
    private static final int CURRENCY_MAX = 10;
    private static final int URL_MAX = 500;
    private static final int SOCIAL_URL_MAX = 300;
    private static final int WHATSAPP_MAX = 30;
    private static final int SEO_TITLE_MAX = 70;
    private static final int SEO_DESCRIPTION_MAX = 160;
    private static final int CUSTOM_CSS_MAX = 10000;
    private static final int ANNOUNCEMENT_MAX = 200;
    private static final int SECTION_CONFIG_MAX = 2000;

    // ─── Shape checks for that legacy payload: values failing one are dropped ───
    private static final Predicate<String> ANY = value -> true;
    private static final Predicate<String> HEX_COLOR = value -> value.matches("^#[0-9A-Fa-f]{6}$");
    private static final Predicate<String> STOREFRONT_URL = value -> value.startsWith("http://")
            || value.startsWith("https://") || value.startsWith("/");
    private static final Predicate<String> HTTP_URL = value -> value.startsWith("http://")
            || value.startsWith("https://");

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final StoreAccessService storeAccessService;
    private final ObjectMapper objectMapper;

    public StoreResponse createStore(String sellerEmail, CreateStoreRequest request) {
        storeAccessService.requireVendor(sellerEmail);
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String name = request.resolveName();
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Store name is required");
        }

        String slug;
        if (request.getStoreSlug() != null && !request.getStoreSlug().isBlank()) {
            slug = sanitizeSlug(request.getStoreSlug());
            int counter = 1;
            String base = slug;
            while (storeRepository.existsBySlug(slug)) {
                slug = base + "-" + counter;
                counter++;
            }
        } else {
            slug = generateUniqueSlug(name);
        }

        Store store = Store.builder()
                .name(name)
                .slug(slug)
                .category(request.resolveCategory())
                .description(request.getDescription())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .country(request.getCountry())
                .currency(request.getCurrency())
                .status("ACTIVE")
                .seller(seller)
                .build();

        storeRepository.save(store);
        log.info("Store created: {} by {}", store.getName(), sellerEmail);

        return mapToResponse(store);
    }

    public StoreResponse updateStore(String sellerEmail, Long storeId, java.util.Map<String, Object> body) {
        Store store = storeAccessService.resolveStore(sellerEmail);
        if (!store.getId().equals(storeId)) {
            throw new ResourceNotFoundException("Store not found");
        }

        // Legacy onboarding payload: every value is capped and shape-checked through
        // applyCapped, which drops what does not fit instead of failing the wizard.
        String name = str(body.get("storeName"));
        if (name == null) name = str(body.get("name"));
        if (name != null && !name.isBlank() && name.trim().length() <= NAME_MAX) {
            store.setName(name.trim());
        }

        String slug = str(body.get("storeSlug"));
        if (slug == null) slug = str(body.get("slug"));
        if (slug != null && !slug.isBlank() && slug.trim().length() <= SLUG_MAX) {
            String candidate = sanitizeSlug(slug);
            if (!candidate.equals(store.getSlug()) && storeRepository.existsBySlug(candidate)) {
                throw new BadRequestException("This store URL is already taken");
            }
            store.setSlug(candidate);
        }

        String category = str(body.get("businessCategory"));
        if (category == null) category = str(body.get("category"));
        if (category != null && !category.isBlank() && category.trim().length() <= CATEGORY_MAX) {
            store.setCategory(category.trim());
        }

        // Storefront template id picked in the dashboard (e.g. "fashion-luxury").
        applyCapped(body, "template", TEMPLATE_MAX, ANY, store::setTemplate);
        String theme = str(body.get("theme"));
        if (theme != null && THEMES.contains(theme.toUpperCase())) {
            store.setTheme(theme.toUpperCase());
        }
        String layout = str(body.get("layout"));
        if (layout != null && LAYOUTS.contains(layout.toUpperCase())) {
            store.setLayout(layout.toUpperCase());
        }

        // Business / contact details shown on the public storefront
        applyCapped(body, "description", DESCRIPTION_MAX, ANY, store::setDescription);
        applyCapped(body, "email", EMAIL_MAX, ANY, store::setEmail);
        applyCapped(body, "phone", PHONE_MAX, ANY, store::setPhone);
        applyCapped(body, "address", ADDRESS_MAX, ANY, store::setAddress);
        applyCapped(body, "country", COUNTRY_MAX, ANY, store::setCountry);
        applyCapped(body, "currency", CURRENCY_MAX, ANY, store::setCurrency);
        applyCapped(body, "logoUrl", URL_MAX, STOREFRONT_URL, store::setLogoUrl);
        applyCapped(body, "bannerUrl", URL_MAX, STOREFRONT_URL, store::setBannerUrl);

        // Branding - colours must be a #rrggbb triplet or they are ignored
        applyCapped(body, "primaryColor", COLOR_MAX, HEX_COLOR, store::setPrimaryColor);
        applyCapped(body, "secondaryColor", COLOR_MAX, HEX_COLOR, store::setSecondaryColor);
        applyCapped(body, "fontFamily", FONT_MAX, ANY, store::setFontFamily);

        // Social links
        applyCapped(body, "facebookUrl", SOCIAL_URL_MAX, HTTP_URL, store::setFacebookUrl);
        applyCapped(body, "instagramUrl", SOCIAL_URL_MAX, HTTP_URL, store::setInstagramUrl);
        applyCapped(body, "twitterUrl", SOCIAL_URL_MAX, HTTP_URL, store::setTwitterUrl);
        applyCapped(body, "whatsappNumber", WHATSAPP_MAX, ANY, store::setWhatsappNumber);

        // SEO + custom CSS (same sanitiser as the validated branding endpoint)
        applyCapped(body, "seoTitle", SEO_TITLE_MAX, ANY, store::setSeoTitle);
        applyCapped(body, "seoDescription", SEO_DESCRIPTION_MAX, ANY, store::setSeoDescription);
        applyCapped(body, "customCss", CUSTOM_CSS_MAX, this::isSafeCustomCss, store::setCustomCss);

        // Storefront sections / announcement introduced alongside the branding endpoint
        applyCapped(body, "announcementBar", ANNOUNCEMENT_MAX, ANY, store::setAnnouncementBar);
        applyCapped(body, "sectionConfig", SECTION_CONFIG_MAX, this::isKnownSectionConfig,
                store::setSectionConfig);

        storeRepository.save(store);
        log.info("Store {} updated by {}", store.getSlug(), sellerEmail);
        return mapToResponse(store);
    }

    /**
     * Applies a text field from the legacy onboarding payload only when the payload
     * actually contains the key, so a partial update never wipes unrelated values.
     * Blank strings clear the column (stored as null); values that exceed the cap or
     * fail the shape check are dropped silently, because onboarding must never fail on
     * a stray field - but garbage is never written either.
     */
    private void applyCapped(Map<String, Object> body, String key, int maxLen,
                             Predicate<String> shape, Consumer<String> setter) {
        if (body == null || !body.containsKey(key)) {
            return;
        }
        String value = str(body.get(key));
        if (value == null || value.isBlank()) {
            setter.accept(null);
            return;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLen) {
            log.debug("Ignoring '{}': longer than {} characters", key, maxLen);
            return;
        }
        if (!shape.test(trimmed)) {
            log.debug("Ignoring '{}': value does not match the expected shape", key);
            return;
        }
        setter.accept(trimmed);
    }

    public StoreResponse setStatus(String sellerEmail, Long storeId, String status) {
        Store store = storeAccessService.resolveStore(sellerEmail);
        if (!store.getId().equals(storeId)) {
            throw new ResourceNotFoundException("Store not found");
        }
        String normalized = status != null ? status.trim().toUpperCase() : "";
        if (!Set.of("ACTIVE", "INACTIVE", "PENDING").contains(normalized)) {
            throw new BadRequestException("Invalid store status: " + status);
        }
        store.setStatus(normalized);
        storeRepository.save(store);
        log.info("Store {} status set to {} by {}", store.getId(), normalized, sellerEmail);
        return mapToResponse(store);
    }

    private String str(Object v) {
        return v != null ? String.valueOf(v) : null;
    }

    private String sanitizeSlug(String input) {
        String cleaned = input.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return cleaned.isBlank() ? "store" + System.currentTimeMillis() : cleaned;
    }

    public StoreResponse getStoreByUser(String sellerEmail) {
        Store store = storeAccessService.resolveStore(sellerEmail);
        return mapToResponse(store);
    }

    public List<StoreResponse> getAllStoresByUser(String sellerEmail) {
        return storeAccessService.resolveStores(sellerEmail)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public StoreResponse updateSettings(String sellerEmail, UpdateStoreSettingsRequest request) {
        Store store = storeAccessService.resolveStore(sellerEmail);
        return applySettings(store, request);
    }

    public StoreResponse updateSettings(String sellerEmail, Long storeId, UpdateStoreSettingsRequest request) {
        Store store = storeAccessService.resolveStore(sellerEmail);
        if (!store.getId().equals(storeId)) {
            throw new ResourceNotFoundException("Store not found");
        }
        return applySettings(store, request);
    }

    private StoreResponse applySettings(Store store, UpdateStoreSettingsRequest request) {
        if (request.getName() != null && !request.getName().isBlank()) {
            store.setName(request.getName().trim());
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            store.setCategory(request.getCategory().trim());
        }
        if (request.getTheme() != null) {
            if (!THEMES.contains(request.getTheme())) {
                throw new BadRequestException("Unknown storefront theme: " + request.getTheme());
            }
            store.setTheme(request.getTheme());
        }
        if (request.getLayout() != null) {
            if (!LAYOUTS.contains(request.getLayout())) {
                throw new BadRequestException("Unknown storefront layout: " + request.getLayout());
            }
            store.setLayout(request.getLayout());
        }

        Map<String, Object> business = request.getBusiness();
        if (business != null) {
            String businessName = str(business.get("businessName"));
            if (businessName != null && !businessName.isBlank()) {
                store.setName(businessName.trim());
            }
            String email = str(business.get("email"));
            if (email != null && !email.isBlank()) store.setEmail(email.trim());
            String phone = str(business.get("phone"));
            if (phone != null) store.setPhone(phone.trim());
            String address = str(business.get("address"));
            if (address != null) store.setAddress(address.trim());
        }

        if (request.getNotifications() != null) {
            try {
                store.setNotifications(objectMapper.writeValueAsString(request.getNotifications()));
            } catch (JsonProcessingException e) {
                throw new BadRequestException("Invalid notifications payload");
            }
        }

        storeRepository.save(store);
        log.info("Store settings updated for {} (theme={}, layout={})",
                store.getSlug(), store.getTheme(), store.getLayout());
        return mapToResponse(store);
    }

    /**
     * Applies the validated storefront branding block: only non-null fields are
     * written (null = leave unchanged), an empty string clears the free-text fields,
     * custom CSS is sanitised and sectionConfig is whitelisted against the sections
     * the storefront can actually render.
     */
    public StoreResponse updateBranding(String sellerEmail, Long storeId,
                                        UpdateStoreBrandingRequest request) {
        Store store = resolveOwnedStore(sellerEmail, storeId);

        applyBranded(request.getTemplate(), store::setTemplate);
        applyBranded(request.getPrimaryColor(), store::setPrimaryColor);
        applyBranded(request.getSecondaryColor(), store::setSecondaryColor);
        applyBranded(request.getFontFamily(), store::setFontFamily);
        applyBranded(request.getLogoUrl(), store::setLogoUrl);
        applyBranded(request.getBannerUrl(), store::setBannerUrl);
        applyBranded(request.getFacebookUrl(), store::setFacebookUrl);
        applyBranded(request.getInstagramUrl(), store::setInstagramUrl);
        applyBranded(request.getTwitterUrl(), store::setTwitterUrl);
        applyBranded(request.getWhatsappNumber(), store::setWhatsappNumber);
        applyBranded(request.getSeoTitle(), store::setSeoTitle);
        applyBranded(request.getSeoDescription(), store::setSeoDescription);

        if (request.getCustomCss() != null) {
            store.setCustomCss(request.getCustomCss().isBlank()
                    ? null : sanitizeCustomCss(request.getCustomCss()));
        }
        if (request.getAnnouncementBar() != null) {
            store.setAnnouncementBar(request.getAnnouncementBar().isBlank()
                    ? null : request.getAnnouncementBar().trim());
        }
        if (request.getSectionConfig() != null) {
            store.setSectionConfig(request.getSectionConfig().isBlank()
                    ? null : validateSectionConfig(request.getSectionConfig()));
        }

        store.setUpdatedAt(LocalDateTime.now());
        storeRepository.save(store);
        log.info("Store {} branding updated by {}", store.getSlug(), sellerEmail);
        return mapToResponse(store);
    }

    /**
     * Applies a field only when the payload carries it, so a partial update never wipes
     * unrelated values. An empty string clears the column (stored as null); a non-empty
     * value is trimmed.
     */
    private void applyBranded(String value, Consumer<String> setter) {
        if (value == null) {
            return;
        }
        setter.accept(value.isBlank() ? null : value.trim());
    }

    /**
     * Guards the custom CSS column. The storefront injects this value into a style
     * block, so anything that could escape it - tags, imports, IE expression(),
     * javascript: or data:text/html URLs - is rejected outright.
     *
     * @throws BadRequestException when the CSS contains a disallowed construct
     */
    private String sanitizeCustomCss(String css) {
        if (!isSafeCustomCss(css)) {
            throw new BadRequestException(
                    "customCss may not contain <, >, @import, expression(), javascript: or data:text/html");
        }
        return css;
    }

    private boolean isSafeCustomCss(String css) {
        if (css == null) {
            return true;
        }
        String lower = css.toLowerCase(Locale.ROOT);
        return !css.contains("<") && !css.contains(">")
                && !lower.contains("@import") && !lower.contains("expression(")
                && !lower.contains("javascript:") && !lower.contains("data:text/html");
    }

    /**
     * sectionConfig must be a JSON array of short lowercase tokens the storefront
     * knows how to render, e.g. {@code ["hero","showcase","testimonials"]}.
     *
     * @throws BadRequestException when it is not such an array
     */
    private String validateSectionConfig(String sectionConfig) {
        if (!isKnownSectionConfig(sectionConfig)) {
            throw new BadRequestException(SECTION_CONFIG_ERROR);
        }
        return sectionConfig;
    }

    private boolean isKnownSectionConfig(String sectionConfig) {
        if (sectionConfig == null || sectionConfig.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(sectionConfig);
            if (node == null || !node.isArray() || node.isEmpty()) {
                return false;
            }
            for (JsonNode element : node) {
                if (!element.isTextual() || !SECTION_TOKENS.contains(element.textValue())) {
                    return false;
                }
            }
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private String generateUniqueSlug(String name) {
        String baseSlug = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        String slug = baseSlug;
        int counter = 1;

        while (storeRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }

        return slug;
    }

    private StoreResponse mapToResponse(Store store) {
        Map<String, Object> business = new HashMap<>();
        business.put("businessName", store.getName());
        business.put("email", store.getEmail() != null ? store.getEmail()
                : (store.getSeller() != null ? store.getSeller().getEmail() : null));
        business.put("phone", store.getPhone() != null ? store.getPhone()
                : (store.getSeller() != null ? store.getSeller().getPhone() : null));
        business.put("address", store.getAddress());

        Map<String, Object> notifications = null;
        if (store.getNotifications() != null && !store.getNotifications().isBlank()) {
            try {
                notifications = objectMapper.readValue(store.getNotifications(),
                        new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse notifications for store {}", store.getId(), e);
            }
        }

        return StoreResponse.builder()
                .id(store.getId())
                .name(store.getName())
                .slug(store.getSlug())
                .category(store.getCategory())
                .theme(store.getTheme())
                .layout(store.getLayout())
                .description(store.getDescription())
                .email(store.getEmail())
                .phone(store.getPhone())
                .address(store.getAddress())
                .country(store.getCountry())
                .currency(store.getCurrency())
                .business(business)
                .notifications(notifications)
                .logoUrl(store.getLogoUrl())
                .bannerUrl(store.getBannerUrl())
                .status(store.getStatus() != null ? store.getStatus() : "ACTIVE")
                .template(store.getTemplate())
                .primaryColor(store.getPrimaryColor())
                .secondaryColor(store.getSecondaryColor())
                .fontFamily(store.getFontFamily())
                .facebookUrl(store.getFacebookUrl())
                .instagramUrl(store.getInstagramUrl())
                .twitterUrl(store.getTwitterUrl())
                .whatsappNumber(store.getWhatsappNumber())
                .seoTitle(store.getSeoTitle() != null ? store.getSeoTitle() : store.getName())
                .seoDescription(store.getSeoDescription() != null ? store.getSeoDescription()
                        : store.getDescription())
                .customCss(store.getCustomCss())
                .announcementBar(store.getAnnouncementBar())
                .sectionConfig(store.getSectionConfig())
                .sellerEmail(store.getSeller().getEmail())
                .createdAt(store.getCreatedAt())
                .updatedAt(store.getUpdatedAt())
                .build();
    }

    public StoreResponse setLogo(String sellerEmail, Long storeId, String url) {
        Store store = resolveOwnedStore(sellerEmail, storeId);
        store.setLogoUrl(url);
        storeRepository.save(store);
        return mapToResponse(store);
    }

    public StoreResponse setBanner(String sellerEmail, Long storeId, String url) {
        Store store = resolveOwnedStore(sellerEmail, storeId);
        store.setBannerUrl(url);
        storeRepository.save(store);
        return mapToResponse(store);
    }

    private Store resolveOwnedStore(String sellerEmail, Long storeId) {
        Store store = storeAccessService.resolveStore(sellerEmail);
        if (!store.getId().equals(storeId)) {
            throw new ResourceNotFoundException("Store not found");
        }
        return store;
    }
}
