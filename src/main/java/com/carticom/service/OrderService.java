package com.carticom.service;

import com.carticom.dto.order.CreateOrderRequest;
import com.carticom.dto.order.OrderItemRequest;
import com.carticom.dto.order.OrderResponse;
import com.carticom.dto.order.OrderItemResponse;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final UserRepository userRepository;
    private final SendByteService sendByteService;
    private final PlanGuard planGuard;

    @Transactional
    public OrderResponse createOrder(String sellerEmail, CreateOrderRequest request) {
        Store store = getStoreBySeller(sellerEmail);
        planGuard.requireOrderCapacity(sellerEmail);

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new java.util.ArrayList<>();

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .filter(p -> p.getStore().getId().equals(store.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemReq.getProductId()));

            if (product.getStockQuantity() < itemReq.getQuantity()) {
                throw new BadRequestException("Insufficient stock for " + product.getName());
            }

            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            subtotal = subtotal.add(itemTotal);

            product.setStockQuantity(product.getStockQuantity() - itemReq.getQuantity());
            product.setSoldCount(product.getSoldCount() + itemReq.getQuantity());
            productRepository.save(product);

            OrderItem orderItem = OrderItem.builder()
                    .productName(product.getName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())
                    .totalPrice(itemTotal)
                    .product(product)
                    .build();
            orderItems.add(orderItem);
        }

        BigDecimal taxAmount = subtotal.multiply(BigDecimal.valueOf(0.075));
        BigDecimal deliveryFee = request.getDeliveryAddress() != null ? BigDecimal.valueOf(500) : BigDecimal.ZERO;
        BigDecimal total = subtotal.add(taxAmount).add(deliveryFee);

        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId()).orElse(null);
        }

        Order order = Order.builder()
                .orderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .store(store)
                .customer(customer)
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .subtotal(subtotal)
                .taxAmount(taxAmount)
                .deliveryFee(deliveryFee)
                .total(total)
                .deliveryAddress(request.getDeliveryAddress())
                .deliveryPhone(request.getDeliveryPhone())
                .deliveryNotes(request.getDeliveryNotes())
                .channel(request.getChannel() != null ?
                        OrderChannel.valueOf(request.getChannel()) : OrderChannel.STOREFRONT)
                .build();

        orderRepository.save(order);

        for (OrderItem item : orderItems) {
            item.setOrder(order);
        }
        orderItemRepository.saveAll(orderItems);
        order.setItems(orderItems);

        log.info("Order created: {} for store {}", order.getOrderNumber(), store.getName());

        return mapToResponse(order);
    }

    public List<OrderResponse> getOrders(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);
        return orderRepository.findByStoreIdOrderByCreatedAtDesc(store.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<OrderResponse> getOrdersForCustomerEmail(String customerEmail) {
        return orderRepository.findByCustomerEmailOrderByCreatedAtDesc(customerEmail)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse getOrder(String sellerEmail, Long orderId) {
        Store store = getStoreBySeller(sellerEmail);
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return mapToResponse(order);
    }

    public OrderResponse updateOrderStatus(String sellerEmail, Long orderId, String status) {
        Store store = getStoreBySeller(sellerEmail);
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        order.setStatus(OrderStatus.valueOf(status));
        orderRepository.save(order);

        try {
            if (order.getCustomer() != null) {
                sendByteService.sendOrderStatusUpdate(
                        order.getCustomer().getEmail(),
                        order.getOrderNumber(),
                        order.getStatus().name()
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send order status email: {}", e.getMessage());
        }

        return mapToResponse(order);
    }

    public OrderResponse updatePaymentStatus(String sellerEmail, Long orderId, String paymentStatus) {
        Store store = getStoreBySeller(sellerEmail);
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        order.setPaymentStatus(PaymentStatus.valueOf(paymentStatus));
        orderRepository.save(order);

        return mapToResponse(order);
    }

    public List<OrderResponse> getOrdersByStatus(String sellerEmail, String status) {
        Store store = getStoreBySeller(sellerEmail);
        return orderRepository.findByStoreIdAndStatus(store.getId(), OrderStatus.valueOf(status))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<OrderResponse> getOrdersForStore(String sellerEmail, Long storeId) {
        requireStoreAccess(sellerEmail, storeId);
        return orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<OrderResponse> getOrdersForStoreAndStatus(String sellerEmail, Long storeId, String status) {
        requireStoreAccess(sellerEmail, storeId);
        return orderRepository.findByStoreIdAndStatus(storeId, OrderStatus.valueOf(status))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void requireStoreAccess(String email, Long storeId) {
        boolean allowed = storeAccessService.resolveStores(email).stream()
                .anyMatch(s -> s.getId().equals(storeId));
        if (!allowed) {
            throw new ResourceNotFoundException("Store not found");
        }
    }

    private Store getStoreBySeller(String sellerEmail) {
        return storeAccessService.resolveStore(sellerEmail);
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus().name())
                .paymentStatus(order.getPaymentStatus().name())
                .subtotal(order.getSubtotal())
                .taxAmount(order.getTaxAmount())
                .deliveryFee(order.getDeliveryFee())
                .total(order.getTotal())
                .deliveryAddress(order.getDeliveryAddress())
                .deliveryPhone(order.getDeliveryPhone())
                .channel(order.getChannel().name())
                .customerName(order.getCustomer() != null ? order.getCustomer().getName() : null)
                .customerEmail(order.getCustomer() != null ? order.getCustomer().getEmail() : null)
                .items(items)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
