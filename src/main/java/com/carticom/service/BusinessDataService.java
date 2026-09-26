package com.carticom.service;

import com.carticom.model.*;
import com.carticom.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessDataService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;

    public Map<String, Object> getFullBusinessContext(Long storeId) {
        Map<String, Object> context = new HashMap<>();

        List<Product> products = productRepository.findByStoreId(storeId);
        List<Order> orders = orderRepository.findByStoreId(storeId);
        List<Customer> customers = customerRepository.findByStoreId(storeId);

        context.put("totalProducts", products.size());
        context.put("totalOrders", orders.size());
        context.put("totalCustomers", customers.size());

        BigDecimal totalRevenue = orders.stream()
            .map(Order::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        context.put("totalRevenue", totalRevenue);

        BigDecimal totalCost = orders.stream()
            .flatMap(o -> o.getItems().stream())
            .filter(item -> item.getCostPriceSnapshot() != null)
            .map(item -> item.getCostPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        context.put("totalCost", totalCost);
        context.put("profit", totalRevenue.subtract(totalCost));

        BigDecimal avgOrderValue = orders.isEmpty() ? BigDecimal.ZERO :
            totalRevenue.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
        context.put("averageOrderValue", avgOrderValue);

        List<Product> lowStockProducts = products.stream()
            .filter(p -> p.getStockQuantity() != null && p.getStockQuantity() <= (p.getLowStockThreshold() != null ? p.getLowStockThreshold() : 5))
            .collect(Collectors.toList());
        context.put("lowStockProducts", lowStockProducts.size());
        context.put("lowStockProductNames", lowStockProducts.stream().map(Product::getName).collect(Collectors.toList()));

        long deliveredOrders = orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
        double deliveryRate = orders.isEmpty() ? 0 : (double) deliveredOrders / orders.size() * 100;
        context.put("deliveryRate", Math.round(deliveryRate * 10) / 10.0);

        long pendingPayments = paymentRepository.findByOrderStoreId(storeId).stream()
            .filter(p -> p.getStatus() == PaymentStatus.PENDING)
            .count();
        context.put("pendingPayments", pendingPayments);

        long cancelledOrders = orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
        double cancellationRate = orders.isEmpty() ? 0 : (double) cancelledOrders / orders.size() * 100;
        context.put("cancellationRate", Math.round(cancellationRate * 10) / 10.0);

        long repeatCustomers = customers.stream()
            .filter(c -> c.getTotalOrders() != null && c.getTotalOrders() >= 2)
            .count();
        double repeatRate = customers.isEmpty() ? 0 : (double) repeatCustomers / customers.size() * 100;
        context.put("repeatCustomerRate", Math.round(repeatRate * 10) / 10.0);

        return context;
    }
}
