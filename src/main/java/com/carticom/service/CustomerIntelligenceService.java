package com.carticom.service;

import com.carticom.model.*;
import com.carticom.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerIntelligenceService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public void updateCustomerStats(Customer customer) {
        List<Order> orders = orderRepository.findByCustomerId(customer.getId());

        if (orders.isEmpty()) {
            customer.setSegment(CustomerSegment.NEW);
            customer.setLifetimeValue(BigDecimal.ZERO);
            customer.setAverageOrderValue(BigDecimal.ZERO);
            customer.setTotalOrders(0);
            customerRepository.save(customer);
            return;
        }

        BigDecimal totalSpent = orders.stream()
            .map(Order::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalOrders = orders.size();
        BigDecimal avgOrder = totalSpent.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);

        LocalDateTime firstOrder = orders.stream()
            .map(Order::getCreatedAt)
            .min(LocalDateTime::compareTo)
            .orElse(null);

        LocalDateTime lastOrder = orders.stream()
            .map(Order::getCreatedAt)
            .max(LocalDateTime::compareTo)
            .orElse(null);

        customer.setLifetimeValue(totalSpent);
        customer.setAverageOrderValue(avgOrder);
        customer.setTotalOrders(totalOrders);
        customer.setFirstOrderDate(firstOrder);
        customer.setLastOrderDate(lastOrder);

        customer.setSegment(computeSegment(totalSpent, totalOrders, lastOrder));

        customerRepository.save(customer);
    }

    private CustomerSegment computeSegment(BigDecimal totalSpent, int totalOrders, LocalDateTime lastOrder) {
        if (lastOrder == null) return CustomerSegment.NEW;

        long daysSinceLastOrder = java.time.temporal.ChronoUnit.DAYS.between(lastOrder, LocalDateTime.now());

        if (totalOrders >= 5 && totalSpent.compareTo(BigDecimal.valueOf(50000)) >= 0) {
            return CustomerSegment.VIP;
        }
        if (totalOrders >= 2) {
            return daysSinceLastOrder > 60 ? CustomerSegment.AT_RISK : CustomerSegment.REPEAT;
        }
        if (daysSinceLastOrder > 90) {
            return CustomerSegment.LOST;
        }
        if (totalOrders == 1 && daysSinceLastOrder <= 7) {
            return CustomerSegment.HIGH_INTENT;
        }
        return CustomerSegment.NEW;
    }
}
