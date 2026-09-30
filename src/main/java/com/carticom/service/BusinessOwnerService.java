package com.carticom.service;

import com.carticom.dto.businessowner.AnalyticsPointDTO;
import com.carticom.dto.businessowner.BusinessOwnerDashboardDTO;
import com.carticom.dto.businessowner.OrderSummaryDTO;
import com.carticom.dto.businessowner.UpdateProfileRequest;
import com.carticom.model.Customer;
import com.carticom.model.Order;
import com.carticom.model.OrderStatus;
import com.carticom.model.Store;
import com.carticom.model.User;
import com.carticom.repository.OrderRepository;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessOwnerService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM dd");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yy");
    private static final DateTimeFormatter YEAR_LABEL = DateTimeFormatter.ofPattern("yyyy");

    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final StoreAccessService storeAccessService;

    // ─── Dashboard ────────────────────────────────────────────

    public BusinessOwnerDashboardDTO getDashboard(String email) {
        Store store = storeAccessService.resolveStore(email);
        List<Order> orders = orderRepository.findByStoreId(store.getId());

        BigDecimal lifetimeRevenue = BigDecimal.ZERO;
        BigDecimal pendingRevenue = BigDecimal.ZERO;
        long pendingOrders = 0L;

        for (Order order : orders) {
            OrderStatus status = order.getStatus();
            if (status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED) {
                continue;
            }
            BigDecimal total = order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO;
            lifetimeRevenue = lifetimeRevenue.add(total);
            if (status == OrderStatus.PENDING || status == OrderStatus.CONFIRMED || status == OrderStatus.PROCESSING) {
                pendingRevenue = pendingRevenue.add(total);
            }
            if (status == OrderStatus.PENDING) {
                pendingOrders++;
            }
        }

        BigDecimal paid = orderRepository.getTotalPaidRevenue(store.getId());

        List<OrderSummaryDTO> recentOrders = orders.stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .limit(5)
                .map(this::toOrderSummary)
                .toList();

        return BusinessOwnerDashboardDTO.builder()
                .lifetimeRevenue(lifetimeRevenue)
                .availableRevenue(paid != null ? paid : BigDecimal.ZERO)
                .pendingRevenue(pendingRevenue)
                .pendingOrders(pendingOrders)
                .recentOrders(recentOrders)
                .build();
    }

    private OrderSummaryDTO toOrderSummary(Order order) {
        Customer customer = order.getCustomer();
        return OrderSummaryDTO.builder()
                .id(order.getId())
                .orderId(order.getOrderNumber())
                .customerName(customer != null && customer.getName() != null ? customer.getName() : "Guest")
                .customerEmail(customer != null && customer.getEmail() != null ? customer.getEmail() : "")
                .total(order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO)
                .currency("NGN")
                .status(order.getStatus() != null ? order.getStatus().name() : "PENDING")
                .items(order.getItems() != null ? order.getItems().size() : 0)
                .createdAt(order.getCreatedAt() != null
                        ? order.getCreatedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString()
                        : "")
                .build();
    }

    // ─── Analytics ────────────────────────────────────────────

    public List<AnalyticsPointDTO> getAnalytics(String email, String period) {
        Store store = storeAccessService.resolveStore(email);
        List<Order> orders = orderRepository.findByStoreId(store.getId());

        List<Bucket> buckets = buildBuckets(period);
        List<AnalyticsPointDTO> points = new ArrayList<>();

        for (Bucket bucket : buckets) {
            BigDecimal revenue = BigDecimal.ZERO;
            long orderCount = 0L;
            Set<String> customers = new HashSet<>();

            for (Order order : orders) {
                if (order.getCreatedAt() == null
                        || !order.getCreatedAt().isBefore(bucket.end())
                        || order.getCreatedAt().isBefore(bucket.start())) {
                    continue;
                }
                OrderStatus status = order.getStatus();
                if (status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED) {
                    continue;
                }
                orderCount++;
                revenue = revenue.add(order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO);
                Customer customer = order.getCustomer();
                String key = customer != null
                        ? (customer.getEmail() != null ? customer.getEmail() : String.valueOf(customer.getId()))
                        : "guest-" + order.getId();
                customers.add(key);
            }

            points.add(AnalyticsPointDTO.builder()
                    .period(bucket.label())
                    .revenue(revenue)
                    .orders(orderCount)
                    .customers((long) customers.size())
                    .conversionRate(0.0)
                    .changes(Map.of())
                    .build());
        }
        return points;
    }

    private record Bucket(String label, LocalDateTime start, LocalDateTime end) {
    }

    private List<Bucket> buildBuckets(String period) {
        String p = period == null || period.isBlank() ? "monthly" : period.trim().toLowerCase();
        List<Bucket> buckets = new ArrayList<>();

        if (p.equals("weekly")) {
            LocalDate today = LocalDate.now();
            for (int i = 6; i >= 0; i--) {
                LocalDate day = today.minusDays(i);
                buckets.add(new Bucket(day.format(DAY_LABEL), day.atStartOfDay(), day.plusDays(1).atStartOfDay()));
            }
        } else if (p.equals("monthly")) {
            LocalDate month = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
            for (int i = 11; i >= 0; i--) {
                LocalDate m = month.minusMonths(i);
                buckets.add(new Bucket(
                        m.format(MONTH_LABEL),
                        m.atStartOfDay(),
                        m.plusMonths(1).atStartOfDay()));
            }
        } else if (p.equals("yearly")) {
            int year = LocalDate.now().getYear();
            for (int i = 4; i >= 0; i--) {
                LocalDate y = LocalDate.of(year - i, 1, 1);
                buckets.add(new Bucket(
                        y.format(YEAR_LABEL),
                        y.atStartOfDay(),
                        y.plusYears(1).atStartOfDay()));
            }
        } else {
            int days = 30;
            if (p.endsWith("d")) {
                try {
                    days = Integer.parseInt(p.substring(0, p.length() - 1));
                } catch (NumberFormatException ignored) {
                }
            }
            days = Math.max(1, Math.min(days, 365));
            LocalDate today = LocalDate.now();
            for (int i = days - 1; i >= 0; i--) {
                LocalDate day = today.minusDays(i);
                buckets.add(new Bucket(day.format(DAY_LABEL), day.atStartOfDay(), day.plusDays(1).atStartOfDay()));
            }
        }
        return buckets;
    }

    // ─── Profile ──────────────────────────────────────────────

    public Map<String, Object> getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new com.carticom.exception.ResourceNotFoundException("User not found"));
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("fullName", user.getFullName());
        profile.put("email", user.getEmail());
        profile.put("phone", user.getPhone() != null ? user.getPhone() : "");
        profile.put("role", user.getRole().name());
        profile.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : "");

        List<Store> stores = storeRepository.findBySellerId(user.getId());
        profile.put("businessName", stores.isEmpty() ? "" : stores.get(0).getName());
        return profile;
    }

    @Transactional
    public Map<String, Object> updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new com.carticom.exception.ResourceNotFoundException("User not found"));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        userRepository.save(user);

        if (request.getBusinessName() != null && !request.getBusinessName().isBlank()) {
            List<Store> stores = storeRepository.findBySellerId(user.getId());
            if (!stores.isEmpty()) {
                Store store = stores.get(0);
                store.setName(request.getBusinessName().trim());
                storeRepository.save(store);
            }
        }
        return getProfile(email);
    }
}
