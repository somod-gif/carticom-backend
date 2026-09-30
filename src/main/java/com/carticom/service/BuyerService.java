package com.carticom.service;

import com.carticom.dto.buyer.BuyerOrderRequest;
import com.carticom.dto.buyer.BuyerOrderItemRequest;
import com.carticom.dto.buyer.BuyerOrderResponse;
import com.carticom.dto.product.ProductResponse;
import com.carticom.dto.store.StorePublicResponse;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.*;
import com.carticom.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuyerService {

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;
    private final SendByteService sendByteService;
    private final CustomerIntelligenceService customerIntelligenceService;
    private final PlanGuard planGuard;

    public StorePublicResponse getPublicStore(String storeSlug) {
        Store store = storeRepository.findBySlug(storeSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        int productCount = productRepository.findByStoreIdAndIsActive(store.getId(), true).size();
        return StorePublicResponse.builder()
                .id(store.getId())
                .name(store.getName())
                .slug(store.getSlug())
                .category(store.getCategory())
                .theme(store.getTheme())
                .layout(store.getLayout())
                .productCount(productCount)
                .createdAt(store.getCreatedAt())
                .build();
    }

    public List<ProductResponse> getStoreProducts(String storeSlug) {
        Store store = storeRepository.findBySlug(storeSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        return productRepository.findByStoreIdAndIsActive(store.getId(), true)
                .stream()
                .map(this::mapProductToResponse)
                .collect(Collectors.toList());
    }

    public ProductResponse getStoreProduct(String storeSlug, Long productId) {
        Store store = storeRepository.findBySlug(storeSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        Product product = productRepository.findById(productId)
                .filter(p -> p.getStore().getId().equals(store.getId()) && p.getIsActive())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return mapProductToResponse(product);
    }

    @Transactional
    public BuyerOrderResponse createBuyerOrder(String storeSlug, BuyerOrderRequest request) {
        Store store = storeRepository.findBySlug(storeSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        planGuard.requireOrderCapacity(store);

        Customer customer = customerRepository.findByStoreIdAndEmail(store.getId(), request.getCustomerEmail())
                .orElseGet(() -> {
                    planGuard.requireCustomerCapacity(store);
                    Customer newCustomer = Customer.builder()
                            .store(store)
                            .name(request.getCustomerName())
                            .email(request.getCustomerEmail())
                            .phone(request.getCustomerPhone())
                            .build();
                    return customerRepository.save(newCustomer);
                });

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (BuyerOrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .filter(p -> p.getStore().getId().equals(store.getId()) && p.getIsActive())
                    .orElseThrow(() -> new BadRequestException("Product not available: " + itemReq.getProductId()));

            if (product.getStockQuantity() < itemReq.getQuantity()) {
                throw new BadRequestException("Insufficient stock for " + product.getName());
            }

            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())
                    .totalPrice(itemTotal)
                    .build();
            orderItems.add(item);

            product.setStockQuantity(product.getStockQuantity() - itemReq.getQuantity());
            product.setSoldCount(product.getSoldCount() + itemReq.getQuantity());
            productRepository.save(product);
        }

        Order order = Order.builder()
                .store(store)
                .customer(customer)
                .orderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .channel(OrderChannel.STOREFRONT)
                .subtotal(totalAmount)
                .deliveryFee(BigDecimal.ZERO)
                .total(totalAmount)
                .deliveryAddress(request.getDeliveryAddress())
                .deliveryPhone(request.getCustomerPhone())
                .deliveryNotes(request.getDeliveryNotes())
                .build();

        order = orderRepository.save(order);

        for (OrderItem item : orderItems) {
            item.setOrder(order);
            orderItemRepository.save(item);
        }
        order.setItems(orderItems);

        try {
            sendByteService.sendOrderNotification(
                    store.getSeller().getEmail(),
                    order.getOrderNumber(),
                    request.getCustomerName(),
                    order.getTotal().toPlainString(),
                    store.getName()
            );
        } catch (Exception e) {
            log.warn("Failed to send order notification email: {}", e.getMessage());
        }

        log.info("Buyer order created: {} for store {}", order.getOrderNumber(), storeSlug);

        try {
            customerIntelligenceService.updateCustomerStats(customer);
        } catch (Exception e) {
            log.warn("Failed to update customer stats: {}", e.getMessage());
        }

        return mapOrderToResponse(order);
    }

    public BuyerOrderResponse trackOrder(String storeSlug, String orderNumber) {
        Store store = storeRepository.findBySlug(storeSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .filter(o -> o.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return mapOrderToResponse(order);
    }

    private ProductResponse mapProductToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .compareAtPrice(product.getCompareAtPrice())
                .stockQuantity(product.getStockQuantity())
                .stock(product.getStockQuantity() != null ? product.getStockQuantity() : 0)
                .sku(product.getSku())
                .barcode(product.getBarcode())
                .imageUrl(product.getImageUrl())
                .category(product.getCategory())
                .isActive(product.getIsActive())
                .isFeatured(product.getIsFeatured())
                .weight(product.getWeight())
                .unit(product.getUnit())
                .lowStockThreshold(product.getLowStockThreshold())
                .soldCount(product.getSoldCount())
                .createdAt(product.getCreatedAt())
                .isLowStock(product.getStockQuantity() != null && product.getLowStockThreshold() != null
                        && product.getStockQuantity() <= product.getLowStockThreshold())
                .build();
    }

    private BuyerOrderResponse mapOrderToResponse(Order order) {
        List<BuyerOrderResponse.BuyerOrderItemResponse> items = order.getItems() != null ?
                order.getItems().stream().map(item -> BuyerOrderResponse.BuyerOrderItemResponse.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .productImage(item.getProduct().getImageUrl())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .totalPrice(item.getTotalPrice())
                        .build()
                ).collect(Collectors.toList()) : List.of();

        return BuyerOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus().name())
                .paymentStatus(order.getPaymentStatus().name())
                .customerName(order.getCustomer() != null ? order.getCustomer().getName() : "")
                .customerEmail(order.getCustomer() != null ? order.getCustomer().getEmail() : "")
                .customerPhone(order.getCustomer() != null ? order.getCustomer().getPhone() : null)
                .deliveryAddress(order.getDeliveryAddress())
                .totalAmount(order.getTotal())
                .items(items)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
