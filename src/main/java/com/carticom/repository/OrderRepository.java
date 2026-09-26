package com.carticom.repository;

import com.carticom.model.Order;
import com.carticom.model.OrderChannel;
import com.carticom.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStoreIdOrderByCreatedAtDesc(Long storeId);

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByStoreIdAndStatus(Long storeId, OrderStatus status);

    List<Order> findByStoreIdAndCreatedAtBetween(Long storeId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT SUM(o.total) FROM Order o WHERE o.store.id = ?1 AND o.paymentStatus = 'PAID'")
    BigDecimal getTotalPaidRevenue(Long storeId);

    @Query("SELECT SUM(o.total) FROM Order o WHERE o.store.id = ?1 AND o.paymentStatus = 'PAID' AND o.createdAt BETWEEN ?2 AND ?3")
    BigDecimal getRevenueBetweenDates(Long storeId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.store.id = ?1")
    Long countOrders(Long storeId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.store.id = ?1 AND o.status = 'DELIVERED'")
    Long countDeliveredOrders(Long storeId);

    List<Order> findByStoreId(Long storeId);

    List<Order> findByCustomerId(Long customerId);

    List<Order> findByCustomerEmailOrderByCreatedAtDesc(String email);

    @Query("SELECT SUM(o.total) FROM Order o WHERE o.store.id = ?1 AND o.channel = ?2 AND o.paymentStatus = 'PAID'")
    BigDecimal getTotalRevenueByChannel(Long storeId, OrderChannel channel);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.paymentStatus = 'PAID'")
    Long countPaidOrders();

    @Query("SELECT SUM(o.total) FROM Order o WHERE o.paymentStatus = 'PAID'")
    BigDecimal getTotalPaidRevenueAll();
}
