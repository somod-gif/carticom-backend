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

    public ProductResponse createProduct(String sellerEmail, CreateProductRequest request) {
        Store store = getStoreBySeller(sellerEmail);

        String sku = request.getSku();
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
                .stockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0)
                .sku(sku)
                .barcode(request.getBarcode())
                .imageUrl(request.getImageUrl())
                .category(request.getCategory())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .isFeatured(request.getIsFeatured() != null ? request.getIsFeatured() : false)
                .weight(request.getWeight())
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
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .compareAtPrice(product.getCompareAtPrice())
                .stockQuantity(stock)
                .sku(product.getSku())
                .barcode(product.getBarcode())
                .imageUrl(product.getImageUrl())
                .category(product.getCategory())
                .isActive(product.getIsActive())
                .isFeatured(product.getIsFeatured())
                .weight(product.getWeight())
                .unit(product.getUnit())
                .lowStockThreshold(threshold)
                .soldCount(product.getSoldCount() != null ? product.getSoldCount() : 0)
                .createdAt(product.getCreatedAt())
                .isLowStock(stock <= threshold)
                .build();
    }

    private String generateSku(Long storeId) {
        return "SKU-" + storeId + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
