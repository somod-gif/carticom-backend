package com.carticom.service;

import com.carticom.dto.store.CreateStoreRequest;
import com.carticom.dto.store.StoreResponse;
import com.carticom.dto.store.UpdateStoreSettingsRequest;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Store;
import com.carticom.model.User;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreService {

    private static final Set<String> THEMES = Set.of("CLASSIC", "MIDNIGHT", "SUNBURST", "BOTANICAL", "MONO");
    private static final Set<String> LAYOUTS = Set.of("GRID", "HERO_GRID", "LIST");

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
        String name = str(body.get("storeName"));
        if (name == null) name = str(body.get("name"));
        if (name != null && !name.isBlank()) store.setName(name.trim());

        String slug = str(body.get("storeSlug"));
        if (slug == null) slug = str(body.get("slug"));
        if (slug != null && !slug.isBlank()) {
            String candidate = sanitizeSlug(slug);
            if (!candidate.equals(store.getSlug()) && storeRepository.existsBySlug(candidate)) {
                throw new BadRequestException("This store URL is already taken");
            }
            store.setSlug(candidate);
        }

        String category = str(body.get("businessCategory"));
        if (category == null) category = str(body.get("category"));
        if (category != null && !category.isBlank()) store.setCategory(category.trim());

        String theme = str(body.get("template"));
        if (theme == null) theme = str(body.get("theme"));
        if (theme != null && THEMES.contains(theme.toUpperCase())) {
            store.setTheme(theme.toUpperCase());
        }
        String layout = str(body.get("layout"));
        if (layout != null && LAYOUTS.contains(layout.toUpperCase())) {
            store.setLayout(layout.toUpperCase());
        }

        storeRepository.save(store);
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
                .sellerEmail(store.getSeller().getEmail())
                .createdAt(store.getCreatedAt())
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
