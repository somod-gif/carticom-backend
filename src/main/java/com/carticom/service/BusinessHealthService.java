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
public class BusinessHealthService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;

    public Map<String, Object> computeHealthScore(Long storeId) {
        Map<String, Object> result = new HashMap<>();

        List<Product> products = productRepository.findByStoreId(storeId);
        List<Order> orders = orderRepository.findByStoreId(storeId);
        List<Customer> customers = customerRepository.findByStoreId(storeId);

        double salesScore = computeSalesScore(orders);
        double retentionScore = computeRetentionScore(customers);
        double inventoryScore = computeInventoryScore(products);
        double profitabilityScore = computeProfitabilityScore(orders);
        double paymentScore = computePaymentScore(storeId);
        double fulfillmentScore = computeFulfillmentScore(orders);

        double overallScore = Math.round(
            (salesScore * 0.25 + retentionScore * 0.2 + inventoryScore * 0.15 +
             profitabilityScore * 0.2 + paymentScore * 0.1 + fulfillmentScore * 0.1) * 10
        ) / 10.0;

        result.put("overallScore", overallScore);
        result.put("salesScore", salesScore);
        result.put("retentionScore", retentionScore);
        result.put("inventoryScore", inventoryScore);
        result.put("profitabilityScore", profitabilityScore);
        result.put("paymentScore", paymentScore);
        result.put("fulfillmentScore", fulfillmentScore);

        List<String> alerts = new ArrayList<>();
        if (inventoryScore < 50) alerts.add("Several products are at risk of stockout");
        if (profitabilityScore < 40) alerts.add("Profit margins are below healthy levels");
        if (retentionScore < 30) alerts.add("Customer retention needs attention");
        if (fulfillmentScore < 60) alerts.add("Delivery performance needs improvement");
        if (salesScore < 40) alerts.add("Sales volume is declining");
        result.put("alerts", alerts);

        return result;
    }

    private double computeSalesScore(List<Order> orders) {
        if (orders.isEmpty()) return 50.0;
        long recentOrders = orders.stream()
            .filter(o -> o.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30)))
            .count();
        long previousOrders = orders.stream()
            .filter(o -> o.getCreatedAt().isAfter(LocalDateTime.now().minusDays(60))
                && o.getCreatedAt().isBefore(LocalDateTime.now().minusDays(30)))
            .count();
        if (previousOrders == 0) return recentOrders > 0 ? 70.0 : 30.0;
        double growth = ((double) (recentOrders - previousOrders) / previousOrders) * 100;
        return Math.min(100, Math.max(0, 50 + growth));
    }

    private double computeRetentionScore(List<Customer> customers) {
        if (customers.isEmpty()) return 50.0;
        long repeatCustomers = customers.stream()
            .filter(c -> c.getTotalOrders() != null && c.getTotalOrders() >= 2)
            .count();
        double repeatRate = (double) repeatCustomers / customers.size() * 100;
        return Math.min(100, repeatRate * 2.5);
    }

    private double computeInventoryScore(List<Product> products) {
        if (products.isEmpty()) return 70.0;
        long lowStock = products.stream()
            .filter(p -> p.getStockQuantity() != null &&
                p.getStockQuantity() <= (p.getLowStockThreshold() != null ? p.getLowStockThreshold() : 5))
            .count();
        long outOfStock = products.stream()
            .filter(p -> p.getStockQuantity() != null && p.getStockQuantity() == 0)
            .count();
        double score = 100.0 - ((double) lowStock / products.size() * 50) - ((double) outOfStock / products.size() * 50);
        return Math.max(0, Math.round(score * 10) / 10.0);
    }

    private double computeProfitabilityScore(List<Order> orders) {
        BigDecimal totalRevenue = orders.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = orders.stream()
            .flatMap(o -> o.getItems().stream())
            .filter(item -> item.getCostPriceSnapshot() != null)
            .map(item -> item.getCostPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalRevenue.compareTo(BigDecimal.ZERO) == 0) return 50.0;
        if (totalCost.compareTo(BigDecimal.ZERO) == 0) return 60.0;
        BigDecimal margin = totalRevenue.subtract(totalCost).multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, RoundingMode.HALF_UP);
        return Math.min(100, Math.max(0, margin.doubleValue() * 3));
    }

    private double computePaymentScore(Long storeId) {
        return 85.0;
    }

    private double computeFulfillmentScore(List<Order> orders) {
        if (orders.isEmpty()) return 70.0;
        long delivered = orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
        long total = orders.size();
        return Math.round((double) delivered / total * 100 * 10) / 10.0;
    }
}
