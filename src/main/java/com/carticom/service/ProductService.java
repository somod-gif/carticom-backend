package com.carticom.service;

import com.carticom.dto.product.CreateProductRequest;
import com.carticom.dto.product.ProductResponse;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Product;
import com.carticom.model.Store;
import com.carticom.model.User;
import com.carticom.repository.ProductRepository;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final UserRepository userRepository;
    private final PlanGuard planGuard;

    public ProductResponse createProduct(String sellerEmail, CreateProductRequest request) {
        Store store = getStoreBySeller(sellerEmail);

        String sku = request.getSku();
        planGuard.requireProductCapacity(sellerEmail);
        if (sku == null || sku.isBlank()) {
            sku = generateSku(store.getId());
            while (productRepository.existsByStoreIdAndSku(store.getId(), sku)) {
                sku = generateSku(store.getId());
            }
        } else if (productRepository.existsByStoreIdAndSku(store.getId(), sku)) {
            throw new BadRequestException("SKU already exists in this store");
        }

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .compareAtPrice(request.getCompareAtPrice())
                .stockQuantity(request.getQuantity() != null ? request.getQuantity()
                        : (request.getStockQuantity() != null ? request.getStockQuantity() : 0))
                .sku(sku)
                .barcode(request.getBarcode())
                .imageUrl(request.getImageUrl())
                .category(request.getCategory())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .isFeatured(request.getIsFeatured() != null ? request.getIsFeatured() : false)
                .weight(request.getWeight() != null ? request.getWeight() : java.math.BigDecimal.ZERO)
                .unit(request.getUnit())
                .lowStockThreshold(request.getLowStockThreshold() != null ? request.getLowStockThreshold() : 5)
                .soldCount(0)
                .store(store)
                .build();

        productRepository.save(product);
        log.info("Product created: {} in store {}", product.getName(), store.getName());

        return mapToResponse(product);
    }

    public List<ProductResponse> getProducts(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);
        return productRepository.findByStoreId(store.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<ProductResponse> getActiveProducts(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);
        return productRepository.findByStoreIdAndIsActive(store.getId(), true)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public ProductResponse getProductById(String sellerEmail, Long productId) {
        Store store = getStoreBySeller(sellerEmail);
        Product product = productRepository.findById(productId)
                .filter(p -> p.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return mapToResponse(product);
    }

    public ProductResponse updateProduct(String sellerEmail, Long productId, CreateProductRequest request) {
        Store store = getStoreBySeller(sellerEmail);
        Product product = productRepository.findById(productId)
                .filter(p -> p.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getCompareAtPrice() != null) product.setCompareAtPrice(request.getCompareAtPrice());
        if (request.getStockQuantity() != null) product.setStockQuantity(request.getStockQuantity());
        if (request.getQuantity() != null) product.setStockQuantity(request.getQuantity());
        if (request.getSku() != null && !request.getSku().isBlank()) product.setSku(request.getSku());
        if (request.getBarcode() != null) product.setBarcode(request.getBarcode());
        if (request.getImageUrl() != null) product.setImageUrl(request.getImageUrl());
        if (request.getCategory() != null) product.setCategory(request.getCategory());
        if (request.getIsActive() != null) product.setIsActive(request.getIsActive());
        if (request.getIsFeatured() != null) product.setIsFeatured(request.getIsFeatured());
        if (request.getWeight() != null) product.setWeight(request.getWeight());
        if (request.getUnit() != null) product.setUnit(request.getUnit());
        if (request.getLowStockThreshold() != null) product.setLowStockThreshold(request.getLowStockThreshold());

        productRepository.save(product);
        return mapToResponse(product);
    }

    public void deleteProduct(String sellerEmail, Long productId) {
        Store store = getStoreBySeller(sellerEmail);
        Product product = productRepository.findById(productId)
                .filter(p -> p.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        productRepository.delete(product);
    }

    public List<ProductResponse> getLowStockProducts(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);
        return productRepository.findByStoreIdAndStockQuantityLessThan(store.getId(), 5)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private Store getStoreBySeller(String sellerEmail) {
        return storeAccessService.resolveStore(sellerEmail);
    }

    private ProductResponse mapToResponse(Product product) {
        Integer stock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        Integer threshold = product.getLowStockThreshold() != null ? product.getLowStockThreshold() : 5;
        Long storeId = product.getStore() != null ? product.getStore().getId() : null;
        return ProductResponse.builder()
                .id(product.getId())
                .storeId(storeId)
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .compareAtPrice(product.getCompareAtPrice())
                .stockQuantity(stock)
                .stock(stock)
                .quantity(stock)
                .sku(product.getSku())
                .barcode(product.getBarcode())
                .imageUrl(product.getImageUrl())
                .category(product.getCategory())
                .isActive(product.getIsActive())
                .active(product.getIsActive())
                .isFeatured(product.getIsFeatured())
                .digital(false)
                .currency("NGN")
                .tenantId(storeId != null ? String.valueOf(storeId) : null)
                .weight(product.getWeight())
                .unit(product.getUnit())
                .lowStockThreshold(threshold)
                .soldCount(product.getSoldCount() != null ? product.getSoldCount() : 0)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .isLowStock(stock <= threshold)
                .build();
    }

    public List<ProductResponse> getProductsByStore(Long storeId) {
        return productRepository.findByStoreId(storeId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<ProductResponse> getActiveProductsByStore(Long storeId) {
        return productRepository.findByStoreIdAndIsActive(storeId, true).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<ProductResponse> searchOwnProducts(String sellerEmail, String q) {
        Store store = getStoreBySeller(sellerEmail);
        String needle = q == null ? "" : q.toLowerCase();
        return productRepository.findByStoreId(store.getId()).stream()
                .filter(p -> p.getName() != null && p.getName().toLowerCase().contains(needle))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<ProductResponse> getProductsByOwnCategory(String sellerEmail, String category) {
        Store store = getStoreBySeller(sellerEmail);
        return productRepository.findByStoreIdAndCategory(store.getId(), category).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public ProductResponse updateInventory(String sellerEmail, Long productId, int quantityDelta) {
        Store store = getStoreBySeller(sellerEmail);
        Product product = productRepository.findById(productId)
                .filter(p -> p.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        int current = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        product.setStockQuantity(Math.max(0, current + quantityDelta));
        productRepository.save(product);
        return mapToResponse(product);
    }

    private String generateSku(Long storeId) {
        return "SKU-" + storeId + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
