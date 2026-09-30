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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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
        return StoreResponse.builder()
                .id(store.getId())
                .name(store.getName())
                .slug(store.getSlug())
                .category(store.getCategory())
                .theme(store.getTheme())
                .layout(store.getLayout())
                .sellerEmail(store.getSeller().getEmail())
                .createdAt(store.getCreatedAt())
                .build();
    }
}
