package com.carticom.service;

import com.carticom.dto.inventory.*;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.*;
import com.carticom.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryLocationRepository locationRepository;
    private final InventoryStockRepository stockRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final UserRepository userRepository;

    public LocationResponse createLocation(String sellerEmail, CreateLocationRequest request) {
        Store store = getStoreBySeller(sellerEmail);

        InventoryLocation location = InventoryLocation.builder()
                .name(request.getName())
                .address(request.getAddress())
                .isDefault(request.getIsDefault() != null ? request.getIsDefault() : false)
                .store(store)
                .build();

        locationRepository.save(location);
        log.info("Location created: {} for store {}", location.getName(), store.getName());

        return LocationResponse.builder()
                .id(location.getId())
                .name(location.getName())
                .address(location.getAddress())
                .isDefault(location.getIsDefault())
                .totalProducts(0)
                .createdAt(location.getCreatedAt())
                .build();
    }

    public List<LocationResponse> getLocations(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);
        return locationRepository.findByStoreId(store.getId())
                .stream()
                .map(loc -> {
                    int productCount = stockRepository.findByLocationId(loc.getId()).size();
                    return LocationResponse.builder()
                            .id(loc.getId())
                            .name(loc.getName())
                            .address(loc.getAddress())
                            .isDefault(loc.getIsDefault())
                            .totalProducts(productCount)
                            .createdAt(loc.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public StockTransferResponse transferStock(String sellerEmail, StockTransferRequest request) {
        Store store = getStoreBySeller(sellerEmail);

        Product product = productRepository.findById(request.getProductId())
                .filter(p -> p.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        InventoryLocation fromLocation = locationRepository.findById(request.getFromLocationId())
                .filter(l -> l.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Source location not found"));

        InventoryLocation toLocation = locationRepository.findById(request.getToLocationId())
                .filter(l -> l.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Destination location not found"));

        InventoryStock fromStock = stockRepository.findByProductIdAndLocationId(product.getId(), fromLocation.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No stock at source location"));

        if (fromStock.getQuantity() < request.getQuantity()) {
            throw new BadRequestException("Insufficient stock at source location");
        }

        fromStock.setQuantity(fromStock.getQuantity() - request.getQuantity());
        stockRepository.save(fromStock);

        InventoryStock toStock = stockRepository.findByProductIdAndLocationId(product.getId(), toLocation.getId())
                .orElse(InventoryStock.builder()
                        .product(product)
                        .location(toLocation)
                        .quantity(0)
                        .reservedQuantity(0)
                        .build());

        toStock.setQuantity(toStock.getQuantity() + request.getQuantity());
        stockRepository.save(toStock);

        log.info("Stock transferred: {} x{} from {} to {}",
                product.getName(), request.getQuantity(), fromLocation.getName(), toLocation.getName());

        return StockTransferResponse.builder()
                .message("Transfer completed successfully")
                .productName(product.getName())
                .fromLocation(fromLocation.getName())
                .toLocation(toLocation.getName())
                .quantity(request.getQuantity())
                .build();
    }

    @Transactional
    public void addStock(String sellerEmail, Long productId, Long locationId, Integer quantity) {
        Store store = getStoreBySeller(sellerEmail);

        Product product = productRepository.findById(productId)
                .filter(p -> p.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        InventoryLocation location = locationRepository.findById(locationId)
                .filter(l -> l.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Location not found"));

        InventoryStock stock = stockRepository.findByProductIdAndLocationId(product.getId(), location.getId())
                .orElse(InventoryStock.builder()
                        .product(product)
                        .location(location)
                        .quantity(0)
                        .reservedQuantity(0)
                        .build());

        stock.setQuantity(stock.getQuantity() + quantity);
        stockRepository.save(stock);

        product.setStockQuantity(product.getStockQuantity() + quantity);
        productRepository.save(product);
    }

    public InventorySummaryResponse getSummary(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);
        List<InventoryLocation> locations = locationRepository.findByStoreId(store.getId());
        List<Product> products = productRepository.findByStoreId(store.getId());

        long lowStockCount = products.stream()
                .filter(p -> p.getStockQuantity() <= p.getLowStockThreshold())
                .count();

        List<LocationStockSummary> locationSummaries = locations.stream()
                .map(loc -> {
                    List<InventoryStock> stocks = stockRepository.findByLocationId(loc.getId());
                    List<ProductStock> productStocks = stocks.stream()
                            .map(s -> ProductStock.builder()
                                    .productId(s.getProduct().getId())
                                    .productName(s.getProduct().getName())
                                    .sku(s.getProduct().getSku())
                                    .quantity(s.getQuantity())
                                    .reservedQuantity(s.getReservedQuantity())
                                    .build())
                            .collect(Collectors.toList());

                    return LocationStockSummary.builder()
                            .locationId(loc.getId())
                            .locationName(loc.getName())
                            .totalProducts(stocks.size())
                            .products(productStocks)
                            .build();
                })
                .collect(Collectors.toList());

        return InventorySummaryResponse.builder()
                .totalLocations(locations.size())
                .totalProducts(products.size())
                .lowStockCount((int) lowStockCount)
                .locations(locationSummaries)
                .build();
    }

    private Store getStoreBySeller(String sellerEmail) {
        return storeAccessService.resolveStore(sellerEmail);
    }
}
