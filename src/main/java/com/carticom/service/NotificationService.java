package com.carticom.service;

import com.carticom.model.NotificationEntity;
import com.carticom.model.Product;
import com.carticom.model.Store;
import com.carticom.model.StoreMember;
import com.carticom.repository.NotificationRepository;
import com.carticom.repository.StoreMemberRepository;
import com.carticom.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Creates in-app notifications for the seller dashboard inbox
 * (GET /api/v1/notifications). Every call is best-effort: a failure here
 * must never break checkout, payment or order flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreRepository storeRepository;

    public void notifyUser(String userId, String type, String title, String body) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            notificationRepository.save(NotificationEntity.builder()
                    .userId(userId.trim())
                    .type(type)
                    .title(title)
                    .body(body)
                    .isRead(false)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to save notification for {}: {}", userId, e.getMessage());
        }
    }

    public void notifyStoreTeam(Store store, String type, String title, String body) {
        if (store == null || store.getId() == null) {
            return;
        }
        notifyStoreTeam(store.getId(), type, title, body);
    }

    /** Notifies the store owner plus every team member (StoreMember). */
    public void notifyStoreTeam(Long storeId, String type, String title, String body) {
        if (storeId == null) {
            return;
        }
        Set<String> recipients = new LinkedHashSet<>();
        try {
            storeRepository.findById(storeId)
                    .map(Store::getSeller)
                    .ifPresent(seller -> {
                        if (seller != null && seller.getEmail() != null && !seller.getEmail().isBlank()) {
                            recipients.add(seller.getEmail().trim());
                        }
                    });
        } catch (Exception e) {
            log.warn("Failed to load store {} for notification: {}", storeId, e.getMessage());
        }
        try {
            for (StoreMember member : storeMemberRepository.findByStoreId(storeId)) {
                if (member.getUser() != null && member.getUser().getEmail() != null
                        && !member.getUser().getEmail().isBlank()) {
                    recipients.add(member.getUser().getEmail().trim());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load store members for notification: {}", e.getMessage());
        }
        recipients.forEach(userId -> notifyUser(userId, type, title, body));
    }

    /** Fires an "alert" notification when a product is at or below its low-stock threshold. */
    public void checkLowStock(Long storeId, Product product) {
        if (product == null || product.getStockQuantity() == null) {
            return;
        }
        int threshold = product.getLowStockThreshold() != null ? product.getLowStockThreshold() : 0;
        if (threshold <= 0 || product.getStockQuantity() > threshold) {
            return;
        }
        notifyStoreTeam(storeId, "alert",
                product.getStockQuantity() == 0
                        ? "Out of stock: " + product.getName()
                        : "Low stock: " + product.getName(),
                product.getStockQuantity() == 0
                        ? product.getName() + " is out of stock. Restock to keep selling."
                        : "Only " + product.getStockQuantity() + " left of " + product.getName() + ".");
    }
}
