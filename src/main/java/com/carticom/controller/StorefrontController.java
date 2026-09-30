package com.carticom.controller;

import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Role;
import com.carticom.model.Store;
import com.carticom.model.User;
import com.carticom.repository.ProductRepository;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import com.carticom.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/storefront")
@RequiredArgsConstructor
public class StorefrontController {

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public record StorefrontStoreDto(Long id, String name, String slug, String category, String theme,
                                     String layout, Integer productCount, String createdAt,
                                     String logoUrl, String bannerUrl, String businessCategory,
                                     String description, String email, String phone, String address,
                                     String country, String currency, String template,
                                     String primaryColor, String secondaryColor, String fontFamily,
                                     String facebookUrl, String instagramUrl, String twitterUrl,
                                     String whatsappNumber, String seoTitle, String seoDescription,
                                     String customCss, String status) {}

    public record StorefrontProductDto(Long id, Long storeId, String name, String slug, String description,
                                       BigDecimal price, BigDecimal compareAtPrice, String currency,
                                       Integer quantity, String sku, String imageUrl, Boolean active,
                                       Boolean digital, String category, String categoryId, String images,
                                       String createdAt, String updatedAt) {}

    @GetMapping("/stores")
    public List<StorefrontStoreDto> listStores(@RequestParam(required = false) String q) {
        return storeRepository.findAll().stream()
                .filter(s -> q == null || q.isBlank()
                        || (s.getName() != null && s.getName().toLowerCase().contains(q.toLowerCase())))
                .map(this::mapStore)
                .collect(Collectors.toList());
    }

    @GetMapping("/stores/{slug}")
    public StorefrontStoreDto getStore(@PathVariable String slug) {
        return mapStore(requireStore(slug));
    }

    @GetMapping("/stores/{slug}/products")
    public List<StorefrontProductDto> getStoreProducts(@PathVariable String slug) {
        Store store = requireStore(slug);
        return productRepository.findByStoreIdAndIsActive(store.getId(), true).stream()
                .map(this::mapProduct)
                .collect(Collectors.toList());
    }

    @GetMapping("/stores/{slug}/categories")
    public List<Map<String, Object>> getStoreCategories(@PathVariable String slug) {
        Store store = requireStore(slug);
        return productRepository.findByStoreIdAndIsActive(store.getId(), true).stream()
                .map(p -> p.getCategory())
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c);
                    m.put("name", c);
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/search")
    public List<StorefrontProductDto> search(@RequestParam String q) {
        String needle = q == null ? "" : q.toLowerCase();
        return productRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .filter(p -> p.getName() != null && p.getName().toLowerCase().contains(needle))
                .filter(p -> p.getStore() != null && p.getStore().getId() != null)
                .map(this::mapProduct)
                .collect(Collectors.toList());
    }

    @PostMapping("/customers/{storeId}/register")
    public Map<String, Object> registerCustomer(@PathVariable Long storeId, @RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "");
        String fullName = body.getOrDefault("fullName", "");
        String phone = body.get("phone");
        if (email.isBlank() || !email.contains("@")) {
            throw new BadRequestException("A valid email is required");
        }
        if (password.length() < 6) {
            throw new BadRequestException("Password must be at least 6 characters");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BadRequestException("An account with this email already exists. Please sign in.");
        }
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName == null || fullName.isBlank() ? email : fullName);
        user.setPhone(phone);
        user.setRole(Role.CUSTOMER);
        user = userRepository.save(user);
        return tokenResponse(user);
    }

    @PostMapping("/customers/{storeId}/login")
    public Map<String, Object> loginCustomer(@PathVariable Long storeId, @RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new UsernameNotFoundException("Invalid email or password");
        }
        return tokenResponse(user);
    }

    @GetMapping("/customers/profile")
    public Map<String, Object> profile(Authentication authentication) {
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Not authenticated");
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("userId", String.valueOf(user.getId()));
        res.put("email", user.getEmail());
        res.put("fullName", user.getFullName());
        res.put("phone", user.getPhone());
        res.put("role", user.getRole() != null ? user.getRole().name() : "CUSTOMER");
        return res;
    }

    private Map<String, Object> tokenResponse(User user) {
        String token = jwtTokenProvider.generateToken(user.getEmail());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("accessToken", token);
        res.put("refreshToken", token);
        res.put("userId", String.valueOf(user.getId()));
        res.put("email", user.getEmail());
        res.put("fullName", user.getFullName());
        res.put("role", user.getRole() != null ? user.getRole().name() : "CUSTOMER");
        return res;
    }

    private Store requireStore(String slug) {
        return storeRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
    }

    private StorefrontStoreDto mapStore(Store s) {
        int productCount = (int) productRepository.findByStoreIdAndIsActive(s.getId(), true).size();
        return new StorefrontStoreDto(
                s.getId(), s.getName(), s.getSlug(), s.getCategory(), s.getTheme(), s.getLayout(),
                productCount, s.getCreatedAt() != null ? s.getCreatedAt().toString() : null,
                s.getLogoUrl(), s.getBannerUrl(), s.getCategory(),
                s.getDescription(), s.getEmail(), s.getPhone(), s.getAddress(), s.getCountry(),
                s.getCurrency(), s.getTemplate(), s.getPrimaryColor(), s.getSecondaryColor(),
                s.getFontFamily(), s.getFacebookUrl(), s.getInstagramUrl(), s.getTwitterUrl(),
                s.getWhatsappNumber(),
                s.getSeoTitle() != null ? s.getSeoTitle() : s.getName(),
                s.getSeoDescription() != null ? s.getSeoDescription() : s.getDescription(),
                s.getCustomCss(),
                s.getStatus() != null ? s.getStatus() : "ACTIVE");
    }

    private StorefrontProductDto mapProduct(com.carticom.model.Product p) {
        Integer qty = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
        Long storeId = p.getStore() != null ? p.getStore().getId() : null;
        String imageUrl = p.getImageUrl();
        String category = p.getCategory();
        return new StorefrontProductDto(
                p.getId(), storeId, p.getName(), null, p.getDescription(),
                p.getPrice(), p.getCompareAtPrice(), "NGN", qty, p.getSku(), imageUrl,
                Boolean.TRUE.equals(p.getIsActive()), false, category, category, imageUrl,
                p.getCreatedAt() != null ? p.getCreatedAt().toString() : null,
                p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
    }
}
