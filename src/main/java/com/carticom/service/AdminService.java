package com.carticom.service;

import com.carticom.dto.admin.AdminOrderResponse;
import com.carticom.dto.admin.AdminStatsResponse;
import com.carticom.dto.admin.AdminStoreResponse;
import com.carticom.dto.admin.AdminUserResponse;
import com.carticom.model.Order;
import com.carticom.model.Role;
import com.carticom.repository.OrderRepository;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;

    public AdminStatsResponse getStats() {
        return AdminStatsResponse.builder()
                .totalUsers(userRepository.count())
                .totalVendors(userRepository.countByRole(Role.VENDOR))
                .totalStaff(userRepository.countByRole(Role.STAFF))
                .totalCustomers(userRepository.countByRole(Role.CUSTOMER))
                .totalAdmins(userRepository.countByRole(Role.ADMIN))
                .totalStores(storeRepository.count())
                .totalOrders(orderRepository.count())
                .paidOrders(orderRepository.countPaidOrders())
                .totalRevenue(orderRepository.getTotalPaidRevenueAll())
                .build();
    }

    public List<AdminUserResponse> getUsers() {
        return userRepository.findAll().stream()
                .map(u -> AdminUserResponse.builder()
                        .id(u.getId())
                        .fullName(u.getFullName())
                        .email(u.getEmail())
                        .role(u.getRole().name())
                        .createdAt(u.getCreatedAt())
                        .build())
                .sorted(Comparator.comparing(AdminUserResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<AdminStoreResponse> getStores() {
        return storeRepository.findAll().stream()
                .map(s -> AdminStoreResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .slug(s.getSlug())
                        .category(s.getCategory())
                        .sellerEmail(s.getSeller().getEmail())
                        .createdAt(s.getCreatedAt())
                        .build())
                .sorted(Comparator.comparing(AdminStoreResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<AdminOrderResponse> getOrders() {
        List<Order> orders = orderRepository.findAll();
        return orders.stream()
                .sorted(Comparator.comparing(Order::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(100)
                .map(o -> AdminOrderResponse.builder()
                        .orderNumber(o.getOrderNumber())
                        .storeName(o.getStore() != null ? o.getStore().getName() : null)
                        .customerName(o.getCustomer() != null ? o.getCustomer().getName() : null)
                        .total(o.getTotal())
                        .status(o.getStatus().name())
                        .paymentStatus(o.getPaymentStatus().name())
                        .channel(o.getChannel().name())
                        .createdAt(o.getCreatedAt())
                        .build())
                .toList();
    }
}
