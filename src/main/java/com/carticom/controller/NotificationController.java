package com.carticom.controller;

import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.NotificationEntity;
import com.carticom.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(Authentication authentication) {
        String userId = requireUser(authentication);
        List<Map<String, Object>> content = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapOne)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> unreadCount(Authentication authentication) {
        String userId = requireUser(authentication);
        return ResponseEntity.ok(notificationRepository.countByUserIdAndIsReadFalse(userId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(Authentication authentication, @PathVariable Long id) {
        String userId = requireUser(authentication);
        NotificationEntity notification = notificationRepository.findById(id)
                .filter(n -> userId.equals(n.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setIsRead(true);
        notification = notificationRepository.save(notification);
        return ResponseEntity.ok(mapOne(notification));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead(Authentication authentication) {
        String userId = requireUser(authentication);
        List<NotificationEntity> notifications = notificationRepository.findByUserIdAndIsReadFalse(userId);
        notifications.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(notifications);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("updated", notifications.size());
        return ResponseEntity.ok(body);
    }

    private String requireUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Not authenticated");
        }
        return authentication.getName();
    }

    private Map<String, Object> mapOne(NotificationEntity n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("customerId", null);
        m.put("userId", n.getUserId());
        m.put("type", n.getType());
        m.put("title", n.getTitle());
        m.put("body", n.getBody());
        m.put("isRead", Boolean.TRUE.equals(n.getIsRead()));
        m.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : null);
        return m;
    }
}
