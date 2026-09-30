package com.carticom.controller;

import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Category;
import com.carticom.model.Store;
import com.carticom.repository.CategoryRepository;
import com.carticom.repository.ProductRepository;
import com.carticom.service.StoreAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final StoreAccessService storeAccessService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(Authentication authentication) {
        Store store = storeAccessService.resolveStore(authentication.getName());
        return ResponseEntity.ok(mapAll(categoryRepository.findByStoreId(store.getId())));
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<Map<String, Object>>> listByStore(@PathVariable Long storeId) {
        return ResponseEntity.ok(mapAll(categoryRepository.findByStoreId(storeId)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            Authentication authentication,
            @RequestBody Map<String, Object> body) {
        Store store = storeAccessService.resolveStore(authentication.getName());
        Category category = new Category();
        category.setStoreId(store.getId());
        category.setName(stringVal(body.get("name")));
        category.setSlug(slugify(stringVal(body.get("name"))));
        category.setDescription(stringVal(body.get("description")));
        category.setImageUrl(stringVal(body.get("imageUrl")));
        category.setCreatedAt(LocalDateTime.now());
        category = categoryRepository.save(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOne(category));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Store store = storeAccessService.resolveStore(authentication.getName());
        Category category = categoryRepository.findById(id)
                .filter(c -> store.getId().equals(c.getStoreId()))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (body.get("name") != null) {
            category.setName(stringVal(body.get("name")));
            category.setSlug(slugify(stringVal(body.get("name"))));
        }
        if (body.containsKey("description")) {
            category.setDescription(stringVal(body.get("description")));
        }
        if (body.containsKey("imageUrl")) {
            category.setImageUrl(stringVal(body.get("imageUrl")));
        }
        category = categoryRepository.save(category);
        return ResponseEntity.ok(mapOne(category));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        Store store = storeAccessService.resolveStore(authentication.getName());
        Category category = categoryRepository.findById(id)
                .filter(c -> store.getId().equals(c.getStoreId()))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        categoryRepository.delete(category);
        return ResponseEntity.noContent().build();
    }

    private List<Map<String, Object>> mapAll(List<Category> categories) {
        return categories.stream().map(this::mapOne).collect(Collectors.toList());
    }

    private Map<String, Object> mapOne(Category c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("storeId", c.getStoreId());
        m.put("name", c.getName());
        m.put("slug", c.getSlug());
        m.put("description", c.getDescription());
        m.put("imageUrl", c.getImageUrl());
        m.put("productCount", productRepository.findByStoreIdAndCategory(c.getStoreId(), c.getName()).size());
        m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        return m;
    }

    private String stringVal(Object v) {
        return v != null ? String.valueOf(v) : null;
    }

    private String slugify(String name) {
        if (name == null) return null;
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
