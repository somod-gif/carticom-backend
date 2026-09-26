package com.carticom.service;

import com.carticom.dto.store.CreateStoreRequest;
import com.carticom.dto.store.StoreResponse;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final StoreAccessService storeAccessService;

    public StoreResponse createStore(String sellerEmail, CreateStoreRequest request) {
        storeAccessService.requireVendor(sellerEmail);
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String slug = generateUniqueSlug(request.getName());

        Store store = Store.builder()
                .name(request.getName())
                .slug(slug)
                .category(request.getCategory())
                .seller(seller)
                .build();

        storeRepository.save(store);
        log.info("Store created: {} by {}", store.getName(), sellerEmail);

        return mapToResponse(store);
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
                .sellerEmail(store.getSeller().getEmail())
                .createdAt(store.getCreatedAt())
                .build();
    }
}
