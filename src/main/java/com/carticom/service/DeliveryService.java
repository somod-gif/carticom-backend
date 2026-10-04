package com.carticom.service;

import com.carticom.dto.delivery.CreateDeliveryRequest;
import com.carticom.dto.delivery.DeliveryResponse;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.*;
import com.carticom.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public DeliveryResponse createDelivery(String sellerEmail, CreateDeliveryRequest request) {
        Store store = getStoreBySeller(sellerEmail);
        Order order = orderRepository.findById(request.getOrderId())
                .filter(o -> o.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        Delivery delivery = Delivery.builder()
                .order(order)
                .status(DeliveryStatus.PENDING)
                .provider(request.getProvider() != null ?
                        DeliveryProvider.valueOf(request.getProvider()) : DeliveryProvider.MANUAL)
                .trackingNumber("TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .pickupAddress(request.getPickupAddress())
                .dropoffAddress(request.getDropoffAddress() != null ? request.getDropoffAddress() : order.getDeliveryAddress())
                .deliveryFee(request.getDeliveryFee())
                .notes(request.getNotes())
                .build();

        deliveryRepository.save(delivery);
        order.setStatus(OrderStatus.SHIPPED);
        orderRepository.save(order);

        log.info("Delivery created: {} for order {}", delivery.getTrackingNumber(), order.getOrderNumber());

        notificationService.notifyStoreTeam(store, "order",
                "Order " + order.getOrderNumber() + " is on the way",
                "Tracking number " + delivery.getTrackingNumber()
                        + (delivery.getProvider() != null ? " via " + delivery.getProvider().name() : ""));

        return mapToResponse(delivery);
    }

    public List<DeliveryResponse> getDeliveries(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);
        return deliveryRepository.findByOrderStoreId(store.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public DeliveryResponse updateStatus(String sellerEmail, Long deliveryId, String status) {
        Store store = getStoreBySeller(sellerEmail);
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .filter(d -> d.getOrder().getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found"));

        delivery.setStatus(DeliveryStatus.valueOf(status));

        if (DeliveryStatus.DELIVERED.name().equals(status)) {
            delivery.getOrder().setStatus(OrderStatus.DELIVERED);
            orderRepository.save(delivery.getOrder());
            notificationService.notifyStoreTeam(store, "order",
                    "Order " + delivery.getOrder().getOrderNumber() + " delivered",
                    "Marked delivered for " + delivery.getDropoffAddress());
        }

        deliveryRepository.save(delivery);
        return mapToResponse(delivery);
    }

    public DeliveryResponse getByTrackingNumber(String sellerEmail, String trackingNumber) {
        Store store = getStoreBySeller(sellerEmail);
        return deliveryRepository.findByTrackingNumber(trackingNumber)
                .filter(d -> d.getOrder() != null && d.getOrder().getStore() != null
                        && store.getId().equals(d.getOrder().getStore().getId()))
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found"));
    }

    private Store getStoreBySeller(String sellerEmail) {
        return storeAccessService.resolveStore(sellerEmail);
    }

    private DeliveryResponse mapToResponse(Delivery delivery) {
        return DeliveryResponse.builder()
                .id(delivery.getId())
                .orderId(delivery.getOrder().getId())
                .orderNumber(delivery.getOrder().getOrderNumber())
                .status(delivery.getStatus().name())
                .provider(delivery.getProvider().name())
                .trackingNumber(delivery.getTrackingNumber())
                .riderName(delivery.getRiderName())
                .riderPhone(delivery.getRiderPhone())
                .pickupAddress(delivery.getPickupAddress())
                .dropoffAddress(delivery.getDropoffAddress())
                .deliveryFee(delivery.getDeliveryFee())
                .estimatedDeliveryTime(delivery.getEstimatedDeliveryTime())
                .notes(delivery.getNotes())
                .createdAt(delivery.getCreatedAt())
                .build();
    }
}
